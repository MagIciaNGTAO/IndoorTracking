/*
 * Copyright 2012 Ocean University of China.
 *
 */

package cn.edu.ouc.algorithm;

/**
 * �ӿڣ�ʵ�ֻص�����
 * 
 * @author Chu Hongwei, Hong Feng
 */
public interface StepTrigger {
	/**
	 * ÿ��̽�⵽�Ų�ʱ����trigger
	 * 
	 * @param strideLength ����
	 * @param orientation ����
	 */
	public void trigger(int stepCount, float strideLength, float[] orientation);
}
