package cn.edu.ouc.algorithm;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import cn.edu.ouc.util.StepDetectionUtil;

/**
 * StepDetection�����ڽŲ�̽��.
 * ��̽�⵽�Ų�ʱ�����ûص�����StepTrigger,���������ͷ�������������.
 * @author will
 *
 */
public class StepDetection {

	private static final String TAG = "StepDetection";
	
	private StepTrigger st; // ʹ�ýӿ�StepTrigger���ⲿ���֪ͨ�Ų�̽�����
	
	@SuppressWarnings("unused")
	private Context context; // ͨ��Context���ʴ���������
	
	private static SensorManager mSensorManager;
	
	/* ----------------------------------------------*/
	// ����checkForStep�����Ĳ���
	private float[] accel = new float[3]; // ���ٶ�ʸ��
	private float[] localMeanAccel; // �ֲ�ƽ�����ٶ�
	//private float[] accelVariance; // ���ٶȷ���
	private int[] condition; // �ж����������ڽ��нŲ�̽��
	private static final int W = 15; // �ֲ����ڴ�С�����ڼ���ֲ�ƽ�����ٶȺͷ���
	private int swSize; // �������ڴ�С
	private float[] slide_windows_acc; // ��������,���ڴ洢�ϼ��ٶ�
	// ��������ָ�룬ָʾ�洢λ�á�
	// ָ���2 * W����ʼ��ǰ2 * W��Ϊ�������ڵĻ���ʹ�á�
	private int swPointer = 2 * W;
	private static final int BLOCKSIZE = 8; // ����1������0����ֵ
	private boolean firstStart = true;
	private int stepCount;
	
	/* ----------------------------------------------*/
	// ����gyroFunction�����Ĳ���
	public static final float EPSILON = 0.000000001f;
	private float timestamp;
	float dT = 0;
	public float axisX = 0;
	public float axisY = 0;
	public float axisZ = 0;
    private static final float NS2S = 1.0f / 1000000000.0f; // ���뵽���ת��
    private final float[] deltaRotationVector = new float[4];
    public float omegaMagnitude = 0;
    public float[] matrix = new float[9]; // ��ת����
    private float[] orientation = new float[3]; // �����
    private float[][] slide_windows_ori; //�������ڣ����ڴ洢����
	
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
		this.slide_windows_acc = new float[swSize];
		this.localMeanAccel = new float[swSize];
		this.slide_windows_ori = new float[swSize][3];
		stepCount = 0;
		
		matrix[0] = 1.0f; matrix[1] = 0.0f; matrix[2] = 0.0f;
		matrix[3] = 1.0f; matrix[4] = 1.0f; matrix[5] = 0.0f;
		matrix[6] = 1.0f; matrix[7] = 0.0f; matrix[8] = 1.0f;
		
		orientation[0] = 0.0f; orientation[1] = 0.0f; orientation[2] = 0.0f;
		//this.accelVariance = new float[swSize];
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
		    	slide_windows_acc[swPointer % (swSize - 1)] = StepDetectionUtil.getMagnitudeOfAccel(accel);
		        if((swPointer == swSize - 1)) {
		        	checkForStep(); // ��ʼ�Ų�̽��
		        }
		        swPointer++;
		        if(swPointer > swSize - 1) { // ���ָ��λ�ó������ڴ�С����ָ���Ƶ����봰����ʼλ��2 * W��
		        	swPointer = (swPointer % (swSize - 1)) + 2 * W; // ���ڵ�ǰ2 * W��λ����Ϊ����ʹ��
		        }
		        swPointer = swPointer % swSize;
		        break;
		 
		    case Sensor.TYPE_GYROSCOPE:
		    	gyroFunction(event); // ��������������
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
		Log.i(TAG, "[StepDetection] startSensor");
		mSensorManager.registerListener(mSensorEventListener, 
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
		
		mSensorManager.registerListener(mSensorEventListener, 
				mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	/**
	 * ע��������
	 */
	public void stopSensor() {
		Log.i(TAG, "[StepDetection] stopSensor");
		mSensorManager.unregisterListener(mSensorEventListener);
	}
	
	/**
	 * �Ų�̽���㷨���������ߵļ��ٶ������жϽŲ�
	 */
	private void checkForStep() {
		Log.i(TAG, "[StepDetection] checkForStep");
		localMeanAccel = StepDetectionUtil.getLocalMeanAccel(slide_windows_acc, W);
		//accelVariance = StepDetectionUtil.getAccelVariance(slide_windows_acc, localMeanAccel, W);
		float threshold = StepDetectionUtil.getAverageLocalMeanAccel(localMeanAccel) + 0.5f;
		condition = StepDetectionUtil.getCondition(localMeanAccel, threshold);
		
		int numOne = 0; // ��¼�ж�����condition�У�����1�ĸ���
		int numZero = 0; // ��¼�ж�����condition�У�����0�ĸ���
		boolean flag = false; // ��¼��ǰ����1����0
		
		// ͨ��������1������0�ĸ����жϽŲ�
		for(int i = 0, j = 1; i < swSize - 1 && j < swSize - W; i++, j++) {
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
			/* ���ǰһ��������i�����ڵ�ǰ������j��ֵ��
			 * ��������1������0�ĸ���������BLOCKSIZE����̽�⵽�Ų���
			 * ��numOne��numZero��0������̽��Ų��Ĳ����ͷ��� */
			if((condition[i] != condition[j]) && j > W && j < swSize - W) {
				if(numOne > BLOCKSIZE && numZero > BLOCKSIZE) {
					numOne = 0;
					numZero = 0;
					stepCount++;
					st.trigger(stepCount, 0, slide_windows_ori[swPointer]);
				}
			}
		}
		
		/* �������е����2 * W����������õ������ǰ2 * Wλ���У�
		 * ģ��ѭ�����С�
		*/
		for(int k = 0; k < 2 * W; k++) {
			slide_windows_acc[k] = slide_windows_acc[k + swSize - 2 * W];
		}
	}
	
	/**
     * gyroFunction�����������ݻ��֣���ȡ�������ݣ�
     * ������������д��orientation
     * @param event �������¼�
     */
    public void gyroFunction(SensorEvent event) {
        if(timestamp != 0) {
			dT = (event.timestamp - timestamp) * NS2S;
			axisX = event.values[0];
			axisY = event.values[1];
			axisZ = event.values[2];
			
			omegaMagnitude = (float) Math.sqrt(axisX * axisX + 
					axisY * axisY + axisZ * axisZ);
			if(omegaMagnitude > EPSILON) 
			{	
				axisX /= omegaMagnitude;
				axisY /= omegaMagnitude;
				axisZ /= omegaMagnitude;
			}
			
			float thetaOverTwo = omegaMagnitude * dT / 2.0f;
			float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
			float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
			deltaRotationVector[0] = sinThetaOverTwo * axisX;
			deltaRotationVector[1] = sinThetaOverTwo * axisY;
			deltaRotationVector[2] = sinThetaOverTwo * axisZ;
			deltaRotationVector[3] = cosThetaOverTwo;
		}
		timestamp = event.timestamp;
		float[] deltaRotationMatrix = new float[9];
		SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, 
				deltaRotationVector);
		
		matrix = StepDetectionUtil.matrixMultiplication(matrix, deltaRotationMatrix);
		SensorManager.getOrientation(matrix, orientation);
		slide_windows_ori[swPointer] = orientation;
    }
	
}
