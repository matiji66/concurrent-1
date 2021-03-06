package basesource.anno.check;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 检测外键
 * @author Jake
 *
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Foreignkey {
	
	/**
	 * 关联类
	 * @return
	 */
	Class<?> cls();
	
	/**
	 * 关联类属性
	 * @return
	 */
	String key();
	
	/**
	 * 错误提示信息
	 * @return
	 */
	String msg() default "";
	

	
}
