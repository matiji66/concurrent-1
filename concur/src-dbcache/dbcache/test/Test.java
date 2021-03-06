package dbcache.test;

import dbcache.DbCacheService;
import dbcache.DbService;
import dbcache.EnhancedEntity;
import dbcache.cache.CacheUnit;
import dbcache.cache.common.CacheLoader;
import dbcache.cache.common.CommonCache;
import dbcache.conf.DbRuleService;
import dbcache.support.asm.EntityAsmFactory;
import dbcache.support.jdbc.JdbcSupport;
import dbcache.utils.CacheUtils;
import dbcache.utils.JdbcUtil;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.apache.mina.util.ConcurrentHashSet;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.ReflectionUtils;
import transfer.Transfer;
import utils.JsonUtils;
import utils.ProtostuffUtils;
import utils.collections.concurrent.ConcurrentWeakHashMap;
import utils.thread.ThreadUtils;

import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:applicationContext.xml" })
@Component
public class Test {

	@Autowired
	private DbCacheService<Entity, Long> cacheService;

	@Autowired
	private DbService dbService;
	
	@Autowired
	private JdbcSupport jdbcSupport;

	public void setCacheService(DbCacheService<Entity, Long> cacheService) {
		this.cacheService = cacheService;
	}



	@Resource(name = "concurrentWeekHashMapCache")
	private CacheUnit cacheUnit;

	@Autowired
	private DbRuleService dbRuleService;
	
	// 用户名 - ID 缓存
	private CommonCache<ConcurrentHashSet<Integer>> userNameCache = CacheUtils.cacheBuilder(new CacheLoader<ConcurrentHashSet<Integer>>() {
		@SuppressWarnings("unchecked")
		@Override
		public ConcurrentHashSet<Integer> load(Object... keys) {
			List list = JdbcUtil.listIdByAttr(Entity.class, "name", keys[0]);
			if (list != null && list.size() > 0) {
				return new ConcurrentHashSet<Integer>(list);
			} else {
				return new ConcurrentHashSet<Integer>();
			}
		}
	}).build();


	public static String getEntityIdKey(Serializable id, Class<?> entityClazz) {
		return entityClazz.getName() +
				"_" +
				id;
	}


	/**
	 * 测试用例
	 * 框架:dbCacheNew
	 * CPU:core i7 4700
	 * 内存:8G
	 * 次数:1亿次修改入库
	 * 耗时:40s
	 * 发送sql数量:530条
	 * @throws InterruptedException
	 */
	@org.junit.Test
	public void testUpdate() throws InterruptedException {


		for(int i = 0;i <= 100000000;i++) {
			for (long j = 1; j < 10; j++) {
				Entity entity = this.dbService.get(Entity.class, j);
				entity.addNum();
//			if(i % 1000000 == 0) {
//				entity.addNum(1);
//			}


//			entity.setNum(i);

//			entity.setUid(i);

//			if(i%100 == 0) {
//			Thread.sleep(10);
//			}
				
//				ConcurrentHashSet<Long> friends = entity.getFriends();
//				friends.add(Long.valueOf(new Random().nextInt(3)));
//				entity.setFriends(friends);

				this.dbService.submitUpdate(entity);
				if (i % 10000000 == 0) {
//				System.out.println("processing");
				System.out.println(ThreadUtils.dumpThreadPool("入库线程池", this.cacheService.getThreadPool()));
				}

				if (i % 1000000 == 0) {
					Thread.sleep(10);
				}

				//			System.gc();
			}
		}
//		System.out.println(entity.num);

//		this.cacheService.flushAllEntity();

//		try {
//			Thread.sleep(500);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

		while(true) {
			try {
				System.out.println(ThreadUtils.dumpThreadPool("入库线程池", this.cacheService.getThreadPool()));
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}



	/**
	 * 测试用例
	 * 框架:dbCacheNew
	 * CPU:core i7 4700
	 * 内存:8G
	 * 次数:1亿次查询缓存
	 * 耗时:19s
	 */
	@org.junit.Test
	public void testGet() {
		Entity entity = new Entity();
		long t1 = System.currentTimeMillis();
		for(int i = 0;i < 100000000;i++) {
			this.cacheService.get(1l);
		}
		System.out.println(System.currentTimeMillis() - t1);
	}


	/**
	 * 测试动态生成静态代理类
	 *
	 *
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	@org.junit.Test
	public void t4() throws InstantiationException,
			IllegalAccessException, NotFoundException, CannotCompileException,
			NoSuchMethodException, SecurityException, IllegalArgumentException,
			InvocationTargetException {

		Class<Entity> rsCls = EntityAsmFactory.getEntityEnhancedClass(Entity.class);

		Class<?>[] paramTypes = { Entity.class };
		Entity orign = new Entity();
		Object[] params = { orign };
		Constructor<Entity> con = rsCls.getConstructor(paramTypes);
		Entity entity = con.newInstance(params);

		entity.setNum(2);

		System.out.println(entity.getNum());
		// System.out.println(entity.getId());

		System.out.println(orign.getNum());
	}


	@org.junit.Test
	public void t5() {
		int hash = 32;
		hash ^= (hash >>> 20) ^ (hash >>> 12);
		int h = hash ^ (hash >>> 7) ^ (hash >>> 4);
		System.out.println(h&16);
		System.out.println(Integer.toBinaryString(h));
		System.out.println(Integer.toBinaryString(h&16));
		System.out.println(Integer.toBinaryString(16));

		Map<Integer,String> map = new HashMap<Integer,String>();
		map.put(32, "32");
		String str = map.get(32);
		System.out.println(str);
		System.out.println('_'+1);
	}



	@org.junit.Test
	public void t6() {
		int hash = 32;
		System.out.println(Integer.toBinaryString(hash));
	}


	@org.junit.Test
	public void t7() {
		System.out.println("C9".hashCode());
		System.out.println("Aw".hashCode());
	}


	@org.junit.Test
	public void t8() {
		System.out.println(Entity.class.hashCode());
		System.out.println(Entity.class.hashCode() * 31);
	}



	@org.junit.Test
	public void t9() {

		Entity entity;

		boolean enter = false;
		for(int i = 0;i < 10000000;i ++) {
			entity = new Entity();
			this.cacheService.submitCreate(entity);
			if(i > 100000) {
				if(!enter) {
					System.out.println("enter");
					enter = true;
				}
				this.cacheService.get(100010000000000l + (i-100000));
				if(i%100 == 0)
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		System.out.println("over");

		entity = this.cacheService.get(300l);
		System.out.println(JsonUtils.object2JsonString(entity));

		try {
			Thread.sleep(1000000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	@org.junit.Test
	public void testChangeIndexValue() {
		long t1 = System.currentTimeMillis();
		Entity entity = this.cacheService.get(1l);
//		System.out.println("use time0 :" + (System.currentTimeMillis() - t1));
		entity.setNum(202);
		entity.setA(new byte[100]);

		List<Entity> list = this.cacheService.listByIndex(Entity.NUM_INDEX, 202);
//		System.out.println("use time1 :" + (System.currentTimeMillis() - t1));
		
		
//		assert list.size() == 1;

		for(Entity entity1 : list) {
			System.out.println(JsonUtils.object2JsonString(entity1));
		}
		System.out.println(entity.getNum());

		entity.getFriends().add(4l);
//		entity.getClass()
		this.cacheService.submitUpdate(entity);
		if (entity instanceof EnhancedEntity) {
			System.out.println(((EnhancedEntity)entity).getEntity());
		}
		System.out.println("use time :" + (System.currentTimeMillis() - t1));
	}
	
	@org.junit.Test
	public void t100() {
		long t1 = System.currentTimeMillis();
		Entity entity = this.cacheService.get(2l);
//		System.out.println("use time0 :" + (System.currentTimeMillis() - t1));
//		entity.setNum(202);
		entity.setA(new byte[100]);

		List<Entity> list = this.cacheService.listByIndex(Entity.NUM_INDEX, 202);
//		System.out.println("use time1 :" + (System.currentTimeMillis() - t1));
		
		
//		assert list.size() == 1;

		for(Entity entity1 : list) {
			System.out.println(JsonUtils.object2JsonString(entity1));
		}
		System.out.println(entity.getNum());

		ConcurrentHashSet<Long> friends = entity.getFriends();
		friends.add(4l);
		entity.setFriends(friends);
//		entity.getClass()
		this.cacheService.submitUpdate(entity);
		if (entity instanceof EnhancedEntity) {
			System.out.println(((EnhancedEntity)entity).getEntity());
		}
		System.out.println("use time :" + (System.currentTimeMillis() - t1));
	}


	@org.junit.Test
	public void t11() {
		"".getBytes();
	}


	@org.junit.Test
	public void t12() {
		ConcurrentWeakHashMap map = new ConcurrentWeakHashMap<Entity,Integer>();

		for(int i = 0;i < 100000;i++) {
			Entity entity = new Entity();
			entity.setId((long) i);

			map.putIfAbsent(entity, i);

		}

	}


	@org.junit.Test
	public void t13() {

//		for(int i = 0;i < 100;i++) {
			Long id = (long) 104;
			Entity entity = new Entity();
			entity.setId(id);

			entity = this.cacheService.submitCreate(entity);
//			Assert.assertEquals(this.cacheService.get(id), entity);
			Assert.assertEquals(entity, this.cacheService.get(id));
//		}

	}


	@org.junit.Test
	public void t14() {

		for(int i = 101;i < 200;i++) {
			Long id = (long) (i + 100);
			Entity entity = new Entity();
			entity.setId(id);

			this.cacheService.submitCreate(entity);

//			Assert.assertEquals(entity, this.cacheService.get(id));
		}

		try {
			Thread.sleep(100000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}


//	public void t15() {
//		CyclicBarrier cb = new CyclicBarrier()
//	}


	@org.junit.Test
	public void t16() {
		System.out.println("16 >>> 1 : " + (16 >>> 1));
	}



	@org.junit.Test
	public void t17() {
		System.out.println(userNameCache.get("test"));
	}

	
	@org.junit.Test
	public void t18() {
		List<List> list = jdbcSupport.listBySql(List.class, "select * from entity");
		System.out.println(list);
	}


	@org.junit.Test
	public void t19() {
		testChangeIndexValue();
		t100();
	}


	@org.junit.Test
	public void t20() {
		ReflectionUtils.doWithFields(SubEntity.class, new ReflectionUtils.FieldCallback() {

			@Override
			public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
				System.out.println("field:" + field.getName());
			}
		});
	}
	
	
	@org.junit.Test
	public void t21() {
		for( int i = 0;i < 1000;i++) {
			testChangeIndexValue();
		}
	}



	@org.junit.Test
	public void t22() throws InterruptedException {


		for(int j = 1;j < 100;j++) {
			Long id = (long) j;
			Entity entity = new Entity();
			entity.doAfterLoad();
			entity.setId(id);

			entity = this.cacheService.submitCreate(entity);
			//			Assert.assertEquals(this.cacheService.get(id), entity);
//			Assert.assertEquals(entity, this.cacheService.get(id));


//			for (int i = 0; i < 1000000; i++) {
//				entity = this.cacheService.get(id);
//
//				entity.increseNum();
//
//
//				this.cacheService.submitUpdate(entity);
//				if (i % 10000000 == 0) {
//					System.out.println(ThreadUtils.dumpThreadPool("入库线程池", this.cacheService.getThreadPool()));
//				}
//
//				if (i % 1000000 == 0) {
//					Thread.sleep(10);
//				}
//
//				entity = null;
//				//			System.gc();
//			}
		}

		while(true) {
			try {
				System.out.println(ThreadUtils.dumpThreadPool("入库线程池", this.cacheService.getThreadPool()));
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}



	@org.junit.Test
	public void t23() throws InterruptedException {

		long t1 = System.currentTimeMillis();
		for(int i = 0;i <= 10000000;i++) {
			for(long j = 1;j < 10;j++) {
				Entity entity = this.cacheService.get(j);
				entity.increseNum();
//			if(i % 1000000 == 0) {
//				entity.addNum(1);
//			}


//			entity.setNum(i);

//			entity.setUid(i);

//			if(i%100 == 0) {
//			Thread.sleep(10);
//			}

				this.cacheService.submitUpdate(entity);
				if (i % 1000000 == 0) {
					System.out.println(ThreadUtils.dumpThreadPool("入库线程池", this.cacheService.getThreadPool()));
				}

//				if (i % 10000 == 0) {
//					Thread.sleep(100);
//				}
			}

//			Thread.sleep(500);
//			System.gc();
		}
//		System.out.println(entity.num);

//		this.cacheService.flushAllEntity();

//		try {
//			Thread.sleep(500);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

		System.out.println(System.currentTimeMillis() - t1);
		
		while(true) {
			try {
				System.out.println(ThreadUtils.dumpThreadPool("入库线程池", this.cacheService.getThreadPool()));
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}


	@org.junit.Test
	public void t24() {
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		long t1 = System.currentTimeMillis();
		for(int i = 0;i <= 100000;i++) {
			for ( long j = 100010000000015l; j < 100010000010000l;j++) {
//				this.dbRuleService.getLongIdFromUser(j);
			}
		}
		System.out.println(System.currentTimeMillis() - t1);
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	
	@org.junit.Test
	public void t25() {
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		long t1 = System.currentTimeMillis();
		for(int i = 0;i <= 100000;i++) {
			for ( long j = 100010000000015l; j < 100010000010000l;j++) {
				Long.valueOf(j);
			}
		}
		System.out.println(System.currentTimeMillis() - t1);
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	
	@org.junit.Test
	public void t26() {
		long t1 = System.currentTimeMillis();
		
		ConcurrentHashSet< Long> set = new ConcurrentHashSet<Long>();
		for(long i = 100000;i < 100030;i++) {
			set.add(i);
		}
		for (int i = 0; i < 10000000;i++) {
			JsonUtils.object2JsonString(set);
		}
		
		System.out.println(System.currentTimeMillis() - t1);
	}


	@org.junit.Test
	public void testHibernateLoad() {
		Entity entity = JdbcUtil.get(Entity.class, 15l);
		byte[] bytes = ProtostuffUtils.object2Bytes(entity);
		System.out.println(bytes.length);


		byte[] bytes1 = Transfer.encode(entity, Entity.class).toBytes();
		System.out.println(bytes1.length);
	}

}
