package transfer.deserializer;

import transfer.Inputable;
import transfer.compile.AsmDeserializerContext;
import transfer.def.TransferConfig;
import transfer.def.Types;
import transfer.exception.IllegalTypeException;
import transfer.utils.BitUtils;
import transfer.utils.IntegerMap;

import java.lang.reflect.Type;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 字节数组解析器
 * Created by Jake on 2015/2/24.
 */
public class ByteArrayDeSerializer implements Deserializer, Opcodes {


    @Override
    public <T> T deserialze(Inputable inputable, Type type, byte flag, IntegerMap referenceMap) {

        byte typeFlag = TransferConfig.getType(flag);

        if (typeFlag != Types.BYTE_ARRAY) {
            throw new IllegalTypeException(typeFlag, Types.BYTE_ARRAY, type);
        }

        // 读取字节数组的大小
        int length = BitUtils.getInt(inputable);

        byte[] bytes = new byte[length];

        inputable.getBytes(bytes);

        return (T) bytes;
    }

    
    @Override
	public void compile(Type type, MethodVisitor mv,
			AsmDeserializerContext context) {
    	
    	mv.visitCode();
    	mv.visitVarInsn(ILOAD, 3);
    	mv.visitMethodInsn(INVOKESTATIC, "transfer/def/TransferConfig", "getType", "(B)B", false);
    	mv.visitVarInsn(ISTORE, 5);
    	mv.visitVarInsn(ILOAD, 5);
    	mv.visitIntInsn(BIPUSH, Types.BYTE_ARRAY);
    	
    	Label l2 = new Label();
    	mv.visitJumpInsn(IF_ICMPEQ, l2);
    	mv.visitTypeInsn(NEW, "transfer/exception/IllegalTypeException");
    	mv.visitInsn(DUP);
    	mv.visitVarInsn(ILOAD, 5);
    	mv.visitIntInsn(BIPUSH, Types.BYTE_ARRAY);
    	mv.visitVarInsn(ALOAD, 2);
    	mv.visitMethodInsn(INVOKESPECIAL, "transfer/exception/IllegalTypeException", "<init>", "(BBLjava/lang/reflect/Type;)V", false);
    	mv.visitInsn(ATHROW);
    	mv.visitLabel(l2);
    	
    	mv.visitFrame(Opcodes.F_APPEND,1, new Object[] {Opcodes.INTEGER}, 0, null);
    	mv.visitVarInsn(ALOAD, 1);
    	mv.visitMethodInsn(INVOKESTATIC, "transfer/utils/BitUtils", "getInt", "(Ltransfer/Inputable;)I", false);
    	mv.visitVarInsn(ISTORE, 6);

    	mv.visitVarInsn(ILOAD, 6);
    	mv.visitIntInsn(NEWARRAY, T_BYTE);
    	mv.visitVarInsn(ASTORE, 7);

    	mv.visitVarInsn(ALOAD, 1);
    	mv.visitVarInsn(ALOAD, 7);
    	mv.visitMethodInsn(INVOKEINTERFACE, "transfer/Inputable", "getBytes", "([B)V", true);

    	mv.visitVarInsn(ALOAD, 7);
    	mv.visitInsn(ARETURN);
    	
    	mv.visitMaxs(5, 8);
    	mv.visitEnd();
    	
	}

    private static ByteArrayDeSerializer instance = new ByteArrayDeSerializer();

    public static ByteArrayDeSerializer getInstance() {
        return instance;
    }

}
