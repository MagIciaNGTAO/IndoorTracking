package cn.edu.ouc.algorithm;

public interface StepTrigger {
	/**
	 * ÿ��̽�⵽�Ų�ʱ����trigger
	 * 
	 * @param length ����
	 * @param orientation ����
	 */
	public void trigger(int stepCount, float length, float[] orientation);
}
