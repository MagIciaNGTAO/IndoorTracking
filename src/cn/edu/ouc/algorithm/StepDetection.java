package cn.edu.ouc.algorithm;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import cn.edu.ouc.util.StepDetectionUtil;

/**
 * StepDetection�����ڽŲ�̽��.
 * ��̽�⵽�Ų�ʱ�����ûص�����StepTrigger,���������ͷ�������������.
 * @author will
 *
 */
public class StepDetection {

	private static final String TAG = "StepDetection";
	
	// ʹ�ýӿ�StepTrigger���ⲿ���֪ͨ�Ų�̽�����
	private StepTrigger st;
	
	// ͨ��Context���ʴ���������
	private Context context;
	
	private static SensorManager mSensorManager;
	
	// ���ٶ�ʸ��
	private float[] accel = new float[3];
	
	// �ֲ�ƽ�����ٶ�
	private float[] localMeanAccel;
	
	// ���ٶȷ���
	private float[] accelVariance;
	
	// �ж����������ڽ��нŲ�̽��
	private int[] condition;
	
	// ������ʸ��
	private float[] gyro = new float[3];
	
	// ����ʸ��
	private float[] orient = new float[3];
	
	// �������ڴ�С
	private int swSize;
	
	// ��������,���ڴ洢�ϼ��ٶ�
	private float[] slide_windows;
	
	// �ֲ����ڴ�С�����ڼ���ֲ�ƽ�����ٶȺͷ���
	private static final int W = 15;
	
	// ��������ָ�룬ָʾ�洢λ�á�
	// ָ���2 * W����ʼ��ǰ2 * W��Ϊ�������ڵĻ���ʹ�á�
	private int swPointer = 2 * W;
	
	// ����1������0����ֵ
	private static final int BLOCKSIZE = 8;
	
	private int stepCount = 0;
	
	private boolean firstStart = true;
	
	/**
	 * ���캯��
	 * @param context
	 * @param stepTrigger �ӿڣ�����ʵ�ֻص�
	 * @param swSize �������ڴ�С
	 */
	public StepDetection(Context context, StepTrigger stepTrigger, int swSize) {
		this.context = context;
		this.st = stepTrigger;
		this.swSize = swSize;
		this.slide_windows = new float[swSize];
		this.localMeanAccel = new float[swSize];
		this.accelVariance = new float[swSize];
		mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
	}
	
	/**
	 * ���������¼�
	 */
	public SensorEventListener mSensorEventListener = new SensorEventListener() {
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			switch(event.sensor.getType()) {
		    case Sensor.TYPE_ACCELEROMETER:
		    	System.arraycopy(event.values, 0, accel, 0, 3);
		        slide_windows[swPointer % (swSize - 1)] = StepDetectionUtil.getMagnitudeOfAccel(accel);
		        if((swPointer == swSize - 1)) {
		        	checkForStep(); // ��ʼ�Ų�̽��
		        }
		        swPointer++;
		        if(swPointer > swSize - 1) {
		        	swPointer = (swPointer % (swSize - 1)) + 2 * W;
		        }
		        swPointer = swPointer % swSize;
		        break;
		 
		    case Sensor.TYPE_GYROSCOPE:
		        break;
		
		    case Sensor.TYPE_ORIENTATION:
		    	break;
			}
		}
		
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			
		}
	};
	
	/**
	 * ע�ᴫ����
	 */
	public void startSensor() {
		mSensorManager.registerListener(mSensorEventListener, 
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
		
		mSensorManager.registerListener(mSensorEventListener, 
				mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				SensorManager.SENSOR_DELAY_FASTEST);
		
		mSensorManager.registerListener(mSensorEventListener, 
				mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
				SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	/**
	 * ע��������
	 */
	public void stopSensor() {
		mSensorManager.unregisterListener(mSensorEventListener);
	}
	
	/**
	 * �Ų�̽���㷨���������ߵļ��ٶ������жϽŲ�
	 */
	private void checkForStep() {
		localMeanAccel = StepDetectionUtil.getLocalMeanAccel(slide_windows, W);
		//accelVariance = StepDetectionUtil.getAccelVariance(slide_windows, localMeanAccel, W);
		float threshold = StepDetectionUtil.getAverageLocalMeanAccel(localMeanAccel) + 0.5f;
		condition = StepDetectionUtil.getCondition(localMeanAccel, threshold);
		
		int numOne = 0; // ��¼�ж�����condition�У�����1�ĸ���
		int numZero = 0; // ��¼�ж�����condition�У�����0�ĸ���
		boolean flag = false; // ��¼��ǰ����1����0
		
		// ͨ��������1������0�ĸ����жϽŲ�
		for(int i = 0, j = 1; i < swSize - 1 && j < swSize -W; i++, j++) {
			if(firstStart) {
				i = 2 * W;
				j = i + 1;
			}
			firstStart = false;
			flag = StepDetectionUtil.isOne(condition[i]); // �ж�ǰһ����������ж�����i�Ƿ�Ϊ1
			/* ���ǰһ��������i���ж������͵�ǰ������j���ж�������ͬ��
			 * ���Ҷ�����1����numOne��1. */
			if((condition[i] == condition[j]) && flag == true) 
			{				
				numOne++;
			}
			/* ���ǰһ��������i���ж������͵�ǰ������j���ж�������ͬ��
			 * ���Ҷ�����0����numZero��1. */
			if((condition[i] == condition[j]) && flag == false) 
			{
				numZero++;	
			}
			/* ����ǰW���ͺ�W�������㣬���ǰһ��������i�����ڵ�ǰ������j��ֵ��
			 * ��������1������0�ĸ���������BLOCKSIZE����̽�⵽�Ų���
			 * ��numOne��numZero��0������̽��Ų��Ĳ����ͷ��� */
			if((condition[i] != condition[j]) && j > W && j < swSize - W) {
				if(numOne > BLOCKSIZE && numZero > BLOCKSIZE) {
					numOne = 0;
					numZero = 0;
					st.trigger(slide_windows[i], 0);
				}
			}
		}
		
		/* �������е����2 * W����������õ������ǰ2 * Wλ���У�
		 * ģ��ѭ�����С�
		*/
		for(int k = 0; k < 2 * W; k++) {
			slide_windows[k] = slide_windows[k + swSize - 2 * W];
		}
	}
	
}
