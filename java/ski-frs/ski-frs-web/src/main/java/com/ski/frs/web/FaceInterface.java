package com.ski.frs.web;

import java.io.UnsupportedEncodingException;

public class FaceInterface {
    
    static {
        System.loadLibrary("FaceInterface");
    }
    
    public static final int DEVICE_GPU = 0;
    public static final int DEVICE_CPU = 1;
    
    /** 成功 */
    public static final int SUCCESS             = 0;
    /** 加载分类器失败 */
    public static final int ERROR_LOAD_FILTER   = 1;
    /** 缓存为空 */
    public static final int ERROR_BUFFER_EMPTY  = 2;
    /** 未初始化 */
    public static final int ERROR_NO_INIT       = 3;
    /** 没有对应索引的GPU设备 */
    public static final int ERROR_NO_DEV        = 4;
    /** GPU设备已经用光，能力不足 */
    public static final int ERROR_DISABLE       = 5;
    /** 没有人脸 */
    public static final int ERROR_NO_FACE       = 6;
    
    public static native long initInstance(int device);
    public static native byte[] fv(long instance, byte[] path);
    public static native int freeInstance(long instance);
    
    public static void main(String[] args) throws UnsupportedEncodingException {
        long instance1 = initInstance(DEVICE_GPU);
        long instance2 = initInstance(DEVICE_GPU);
        System.out.println(String.format("init1 = 0x%016X", instance1));
        System.out.println(String.format("init2 = 0x%016X", instance2));
        System.out.println("fv1 = " + new String(fv(instance1, args[0].getBytes("gb2312"))));
        System.out.println("fv2 = " + new String(fv(instance2, args[0].getBytes("gb2312"))));
        System.out.println("free1 = " + freeInstance(instance1));
        System.out.println("free2 = " + freeInstance(instance2));
    }

}