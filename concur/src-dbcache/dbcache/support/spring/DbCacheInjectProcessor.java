package dbcache.support.spring;

import dbcache.DbCacheInitError;
import dbcache.DbCacheService;
import dbcache.EntityLoadListener;
import dbcache.IEntity;
import dbcache.conf.DbConfigFactory;
import dbcache.conf.impl.CacheConfig;
import dbcache.index.IndexChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import utils.reflect.GenericsUtils;
import utils.reflect.ReflectionUtility;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * DbCacheService自动注入处理器
 * @author Jake
 * @date 2014年8月24日下午7:58:00
 */
@Component
public class DbCacheInjectProcessor extends InstantiationAwareBeanPostProcessorAdapter {

	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DbCacheInjectProcessor.class);

	@Autowired
	private DbConfigFactory configFactory;


	@Override
	public Object postProcessAfterInitialization(final Object bean, final String beanName)
			throws BeansException {

		// 处理DbCacheService属性
		ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
			public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
				processDbCacheService(bean, beanName, field);
			}
		});

		// 处理EntityLoadEventListener接口
		processEntityLoadEventListener(bean);

		// 处理IndexChangeListener接口
		processIndexChangeListener(bean);

		return super.postProcessAfterInitialization(bean, beanName);
	}


	/**
	 * 处理DbCacheService属性
	 * @param bean bean
	 * @param beanName beanName
	 * @param field field
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void processDbCacheService(Object bean, String beanName,
									   Field field) {

		if (!field.getType().equals(DbCacheService.class)) {
			return;
		}


		DbCacheService serviceBean;
		Class<? extends IEntity> clz = null;
		try {
			Type type = field.getGenericType();
			Type[] types = ((ParameterizedType) type).getActualTypeArguments();
			clz = (Class<? extends IEntity>) types[0];

			serviceBean = this.configFactory.getDbCacheServiceBean(clz);

		} catch (Exception e) {
			FormattingTuple message = MessageFormatter.arrayFormat("Bean[{}]的注入属性[{}<{}, ?>]类型声明错误", new Object[]{beanName, field.getName(), clz != null ?clz.getSimpleName() : null});
			logger.error(message.getMessage());
			throw new IllegalStateException(message.getMessage(), e);
		}

		if (serviceBean == null) {
			FormattingTuple message = MessageFormatter.format("实体[{}]缓存服务对象不存在", clz.getName());
			logger.debug(message.getMessage());
			throw new IllegalStateException(message.getMessage());
		}

		// 注入DbCacheService
		ReflectionUtility.inject(bean, field, serviceBean);

		// 注册DbCacheServiceBean
		this.configFactory.registerDbCacheServiceBean(clz, serviceBean);
	}


	/**
	 * 收集EntityLoadEventListener bean
	 * @param bean
	 */
	private void processEntityLoadEventListener(Object bean) {

		if (!(bean instanceof EntityLoadListener)) {
			return;
		}

		EntityLoadListener entityLoadEventListenerBean = (EntityLoadListener) bean;

		Class<?> listenClass = GenericsUtils.getSuperClassGenricType(entityLoadEventListenerBean.getClass(), 0);
		if (listenClass == null) {
			return;
		}

		CacheConfig<?> cacheConfig =  configFactory.getCacheConfig(listenClass);
		if (cacheConfig == null) {
			throw new DbCacheInitError("无法监听加载的实体类型:" + listenClass);
		}
		cacheConfig.setHasLoadListeners(true);
		cacheConfig.getEntityLoadEventListeners().add(entityLoadEventListenerBean);

	}

	/**
	 * 收集IndexChangeListener bean
 	 */
	private void processIndexChangeListener(Object bean) {
		if (!(bean instanceof IndexChangeListener)) {
			return;
		}

		IndexChangeListener indexChangeListenerBean = (IndexChangeListener) bean;

		Class<?> listenClass = GenericsUtils.getInterfaceGenricType(indexChangeListenerBean.getClass(), IndexChangeListener.class, 0);
		if (listenClass == null) {
			return;
		}

		CacheConfig<?> cacheConfig =  configFactory.getCacheConfig(listenClass);
		if (cacheConfig == null) {
			throw new DbCacheInitError("无法监听加载的实体类型:" + listenClass);
		}
		cacheConfig.setHasIndexListeners(true);
		cacheConfig.getIndexChangeListener().add(indexChangeListenerBean);
	}


}
