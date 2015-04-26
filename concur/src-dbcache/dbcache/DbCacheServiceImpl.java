package dbcache;

import dbcache.anno.ThreadSafe;
import dbcache.cache.CacheUnit;
import dbcache.conf.CacheConfig;
import dbcache.conf.ConfigFactory;
import dbcache.conf.Inject;
import dbcache.dbaccess.DbAccessService;
import dbcache.index.DbIndexService;
import dbcache.index.IndexValue;
import dbcache.persist.PersistStatus;
import dbcache.persist.service.DbPersistService;
import utils.enhance.asm.ValueGetter;
import utils.JsonUtils;
import utils.collections.concurrent.ConcurrentHashMapV8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 数据库缓存服务实现类
 * <br/>更改实体属性值需要外部加锁
 * @author jake
 * @date 2014-7-31-下午6:07:37
 */
@ThreadSafe
@Component
public class DbCacheServiceImpl<T extends IEntity<PK>, PK extends Comparable<PK> & Serializable>
		implements DbCacheService<T, PK>, ApplicationListener<ContextClosedEvent> {

	/**
	 * 实现原则:
	 * 1,不需要用锁的地方尽量不用到锁;横向扩展设计,减少并发争用资源 ↑
	 * 2,维护缓存原子性,数据入库采用类似异步事件驱动方式 ↑
	 * 3,支持大批量操作数据 ↑
	 * 4,积极解耦,模块/组件的方式,基于接口的设计,易于维护和迁移 ↑
	 * 5,用户友好性.不需要了解太多的内部原理,不需要太多配置 ↑
	 * 6,可监控,易于问题排查 ↑
	 * 7,注重性能和内存占用控制以及回收效率 ↑
	 * 8,简单为主,不过度封装 ↓
	 * 9,懒加载,预编译操作  ↓
	 */

	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DbCacheServiceImpl.class);

	/**
	 * 实体类形
	 * 需要外部设定值
	 */
	@Inject
	private Class<T> clazz;

	/**
	 * 实体缓存配置
	 */
	@Inject
	private CacheConfig<T> cacheConfig;

	@Autowired
	private ConfigFactory configFactory;

	@Autowired
	@Qualifier("jdbcDbAccessServiceImpl")
	private DbAccessService dbAccessService;

	@Inject
	@Autowired
	@Qualifier("concurrentLruHashMapCache")
	private CacheUnit cacheUnit;

	/**
	 * 默认的持久化服务
	 */
	@Inject
	@Autowired
	@Qualifier("inTimeDbPersistService")
	private DbPersistService dbPersistService;

	@Autowired
	@Qualifier("inTimeDbPersistService")
	private DbPersistService inTimeDbPersistService;

	/**
	 * 索引服务
	 */
	@Inject
	@Autowired
	private DbIndexService<PK> indexService;


	/**
	 * 等待锁map {key:lock}
	 */
	private final ConcurrentMap<Object, Lock> WAITING_LOCK_MAP = new ConcurrentHashMapV8<Object, Lock>();


	@Override
	public T get(PK id) {

		final CacheObject<T> cacheObject = this.getCacheObject(id);
		if (cacheObject != null && cacheObject.getPersistStatus() != PersistStatus.DELETED) {
			return (T) cacheObject.getProxyEntity();
		}

		return null;
	}
	

	/**
	 * 获取缓存对象
	 * @param key 实体id
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private CacheObject<T> getCacheObject(PK key) {

		// 从共用缓存获取
		CacheUnit.ValueWrapper wrapper = (CacheUnit.ValueWrapper) cacheUnit.get(key);
		if (wrapper != null) {	// 已经缓存
			return (CacheObject<T>) wrapper.get();
		}

		// 获取缓存唯一锁
		Lock lock = new ReentrantLock();;
		Lock prevLock = WAITING_LOCK_MAP.putIfAbsent(key, lock);
		lock = prevLock != null ? prevLock : lock;
		
		// 查询数据库
		lock.lock();
		try {

			wrapper = (CacheUnit.ValueWrapper) cacheUnit.get(key);
			if (wrapper != null) {
				return (CacheObject<T>) wrapper.get();
			}

			T entity = dbAccessService.get(clazz, key);
			if (entity == null) {
				// 缓存NULL value
				wrapper = cacheUnit.putIfAbsent(key, null);
				if (wrapper != null) {
					return (CacheObject<T>) wrapper.get();
				}
			}
			
			
			// 创建缓存对象
			CacheObject<T> cacheObject = configFactory.createCacheObject(entity, clazz, indexService, key, cacheUnit, cacheConfig);
			wrapper = cacheUnit.putIfAbsent(key, cacheObject);
			
			if (wrapper == null || wrapper.get() == null) {
				return null;
			}
			
			cacheObject = (CacheObject<T>) wrapper.get();
			// 初始化
			cacheObject.doInit(cacheConfig);

			// 更新索引 需要外层加锁
			if(cacheConfig.isEnableIndex()) {
				for (ValueGetter<T> indexGetter : cacheConfig.getIndexList()) {
					this.indexService.create(IndexValue.valueOf(indexGetter.getName(), indexGetter.get(entity), key));
				}
			}

			// 实体加载监听接口回调
			if (cacheConfig.isHasListeners()) {
				for (EntityLoadListener listener : cacheConfig.getEntityLoadEventListeners()) {
					listener.onLoad(entity);
				}
			}
			
			return cacheObject;
		} finally {
			WAITING_LOCK_MAP.remove(key);
			lock.unlock();
		}

	}


	@Override
	public List<T> listById(Collection<PK> idList) {

		if (idList == null || idList.size() == 0) {
			return null;
		}

		final List<T> list = new ArrayList<T> (idList.size());

		for (PK id : idList) {
			T entity = this.get(id);
			if (entity != null) {
				list.add(entity);
			}
		}

		return list;
	}


	@Override
	public List<T> listByIndex(String indexName, Object indexValue) {

		final Collection<PK> idList = this.indexService.get(indexName, indexValue);
		if(idList == null || idList.isEmpty()) {
			return Collections.emptyList();
		}

		final List<T> result = new ArrayList<T>(idList.size());
		for(PK id : idList) {
			T temp = this.get(id);
			if(temp != null) {
				result.add(temp);
			}
		}
		
		return result;
	}


	@Override
	public Collection<PK> listIdByIndex(String indexName, Object indexValue) {
		return this.indexService.get(indexName, indexValue);
	}


	@SuppressWarnings("unchecked")
	@Override
	public T submitCreate(T entity) {

		// 生成主键
		if (entity.getId() == null) {

			Object id = cacheConfig.getIdAutoGenerateValue();
			if (id == null) {
				String msg = "提交新建实体到更新队列参数错：未能识别主键类型";
				logger.error(msg);
				throw new IllegalArgumentException(msg);
			}

			entity.setId( (PK) id);
		}

		// 存储到缓存
		CacheObject<T> cacheObject = null;
		final Object key = entity.getId();

		CacheUnit.ValueWrapper wrapper = (CacheUnit.ValueWrapper) cacheUnit.get(key);
		if(wrapper != null) {
			cacheObject = (CacheObject<T>) wrapper.get();
		}

		
		boolean exists = false;// 缓存中是否还有实体
		if (wrapper == null) {// 缓存还不存在
			cacheObject = configFactory.createCacheObject(entity, this.clazz, indexService, key, cacheUnit, cacheConfig);
			wrapper = cacheUnit.putIfAbsent(key, cacheObject);
			if (wrapper != null && wrapper.get() != null) {
				cacheObject = (CacheObject<T>) wrapper.get();
			}
		} else if(cacheObject == null) {// 缓存为NULL
			cacheObject = configFactory.createCacheObject(entity, this.clazz, indexService, key, cacheUnit, cacheConfig);
			wrapper = cacheUnit.replace(key, null, cacheObject);
			if (wrapper != null && wrapper.get() != null) {
				cacheObject = (CacheObject<T>) wrapper.get();
			}
		}  else {
			if(cacheObject.getPersistStatus() == PersistStatus.DELETED) {// 已被删除
				// 删除再保存，实际上很少出现这种情况
				cacheObject.setPersistStatus(PersistStatus.TRANSIENT);
			}
			exists = true;
		}

		if (cacheObject == null) {
			return null;
		}

		// 入库 
		if (!exists) {
			// 加载回调
			cacheObject.doAfterLoad();
		}

		// 更新索引
		if (cacheConfig.isEnableIndex()) {
			entity = cacheObject.getEntity();
			for(ValueGetter<T> indexGetter : cacheConfig.getIndexList()) {
				this.indexService.create(IndexValue.valueOf(indexGetter.getName(), indexGetter.get(entity), entity.getId()));
			}
		}

		if (!exists) {
			// 实体加载监听接口回调
			if (cacheConfig.isHasListeners()) {
				for (EntityLoadListener listener : cacheConfig.getEntityLoadEventListeners()) {
					listener.onLoad(entity);
				}
			}
		}

		// 提交持久化
		inTimeDbPersistService.handleSave(cacheObject, this.dbAccessService, this.cacheConfig);
		
		if (cacheObject.getPersistStatus() == PersistStatus.DELETED) {
			return null;
		}
		return cacheObject.getProxyEntity();
	}


	@Override
	public void submitUpdate(T entity) {

		final CacheObject<T> cacheObject = this.getCacheObject(entity.getId());
		if (cacheObject == null) {
			return;
		}
		
		// 验证缓存操作原子性(缓存实体必须唯一)
		if(cacheObject.getProxyEntity() != entity) {
			String msg = "实体使用期间缓存对象CacheObject被修改过:无法保证原子性和实体唯一,请重试[user:"
					+ JsonUtils.object2JsonString(entity) + ", current:" + JsonUtils.object2JsonString(cacheObject.getProxyEntity()) + "]";
			logger.error(msg);
			throw new IllegalStateException(msg);// 抛出异常则为内部实现错误
		}

		// 提交持久化任务
		dbPersistService.handleUpdate(cacheObject, this.dbAccessService, this.cacheConfig);
	}


	@Override
	public void submitDelete(T entity) {
		this.submitDelete(entity.getId());
	}


	@Override
	public void submitDelete(final PK id) {

		final CacheObject<T> cacheObject = this.getCacheObject(id);
		if (cacheObject == null) {
			return;
		}

		// 是否已经被删除
		if(cacheObject.getPersistStatus() == PersistStatus.DELETED) {
			return;
		}

		// 标记为已经删除
		cacheObject.setPersistStatus(PersistStatus.DELETED);

		// 更新索引
		if(cacheConfig.isEnableIndex()) {
			T entity = cacheObject.getEntity();
			for(ValueGetter<T> indexGetter : cacheConfig.getIndexList()) {
				this.indexService.remove(IndexValue.valueOf(indexGetter.getName(), indexGetter.get(entity), entity.getId()));
			}
		}

		// 提交持久化任务
		inTimeDbPersistService.handleDelete(cacheObject, this.dbAccessService, id, this.cacheUnit);
	}



	/**
	 * dbCache 初始化
	 * 系统生成DbCacheService实例时将调用
	 */
	public void init() {
		//注册jvm关闭钩子
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				dbPersistService.logHadNotPersistEntity();
			}
		});
	}


	@Override
	public void onApplicationEvent(ContextClosedEvent event) {
		this.onCloseApplication();
	}


	/**
	 * 关闭应用时回调
	 */
	public void onCloseApplication() {
		//等待入库执行完毕
		dbPersistService.destroy();
		//输出为持久化的实体日志
		dbPersistService.logHadNotPersistEntity();
	}
	
	
	@Override
	public ExecutorService getThreadPool() {
		return this.dbPersistService.getThreadPool();
	}


	@Override
	public CacheUnit getCacheUnit() {
		return cacheUnit;
	}


	public CacheConfig<T> getCacheConfig() {
		return cacheConfig;
	}

	@Override
	public DbIndexService<PK> getIndexService() {
		return indexService;
	}


	@Override
	public String toString() {
		Map<String, Object> toStrMap = new HashMap<String, Object>();
		toStrMap.put("clazz", this.clazz);
		toStrMap.put("proxyClazz", this.cacheConfig.getProxyClazz());
		toStrMap.put("WAITING_LOCK_MAP_SIZE", this.WAITING_LOCK_MAP.size());
		toStrMap.put("cacheUseSize", this.cacheUnit.getCachedSize());
		toStrMap.put("indexServiceCacheUseSize", this.indexService.getCacheUnit().getCachedSize());
		return JsonUtils.object2JsonString(toStrMap);
	}



}
