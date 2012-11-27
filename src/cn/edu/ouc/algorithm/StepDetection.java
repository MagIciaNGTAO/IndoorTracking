/*
 * Copyright 2012 Ocean University of China.
 *
 */

package cn.edu.ouc.algorithm;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import cn.edu.ouc.db.DatabaseHelper;
import cn.edu.ouc.util.StepDetectionUtil;

/**
 * StepDetection�����ڽŲ�̽��.
 * ��̽�⵽�Ų�ʱ�����ûص�����StepTrigger,���������ͷ�������������.
 * 
 * @author Chu Hongwei, Hong Feng
 * @ University of China
 */
public class StepDetection {

	private static final String TAG = StepDetection.class.getSimpleName();
	
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
	private boolean firstStart = true; //�жϳ����Ƿ��״����У��Ա�Ի������ڵ���ʼλ�ý����趨
	private int stepCount; //̽��Ų���
	private double strideLength; //����
	
	/* ----------------------------------------------*/
	// ����gyroFunction�����Ĳ���
	public static final float EPSILON = 0.000000001f;
	private float[] gyro = new float[3]; // ����������
	private float timestamp;
    private static final float NS2S = 1.0f / 1000000000.0f; // ���뵽���ת��
    public float[] matrix = new float[9]; // ��ת����
    private float[] orientation = new float[3]; // �����
    private float[][] slide_windows_ori; //�������ڣ����ڴ洢����
    
    /* ----------------------------------------------*/
	// ���ݿ������ز���
    DatabaseHelper mHelper;
	SQLiteDatabase db;
	private static final String TBL_NAME = "track_tbl";
	double lat = 36.16010;
    double lng = 120.491951;
    
    /* ----------------------------------------------*/
	// HDE���򲹳���ز���
	/* ����־: 
	 * ���EΪ�������߷���ƫ�����������ࣩ,������IController���ӹ̶�����IC.
	 * ���EΪ����������IController���ٹ̶�����IC.
	 */
	private float E = 0.0f;
	private float IC = -0.00009f; // �̶���������ʾ������IController�Ĳ�������
	private static final float DELTA = 90f; // ���������Ƕȣ����������ഹֱ
	float IController = 0; // ����������������ƫ�����
	private int SIGN = 0; // �ж����߷���ƫ�����������һ�࣬SIGN = 1ƫ����࣬SIGN = 0ƫ���Ҳ�
	private float priOrientation = 0f; // ǰһ���ķ���
	private boolean STEPDETECTED = false; // �Ų�̽���־
	 
	
    
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
		mHelper = new DatabaseHelper(context);
		db = mHelper.getWritableDatabase();
		
		matrix[0] = 1.0f; matrix[1] = 0.0f; matrix[2] = 0.0f;
		matrix[3] = 0.0f; matrix[4] = 1.0f; matrix[5] = 0.0f;
		matrix[6] = 0.0f; matrix[7] = 0.0f; matrix[8] = 1.0f;
		
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
		db.close();
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
			if(firstStart) { // �״����г���ʱ���������ڵĳ�ʼλ������Ϊ2 * W��
				i = 2 * W;   // ǰ2 * W ����������
				j = i + 1;
			}
			firstStart = false;
			
			HDEComp(); // HDEУ������
			
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
					STEPDETECTED = true;
					stepCount++;
					float meanA = StepDetectionUtil.getMean(localMeanAccel, j, W);
					strideLength = StepDetectionUtil.getSL(0.33f, meanA);
					st.trigger(stepCount, (float) strideLength, orientation);
					priOrientation = (float) ((orientation[0] * 180 / Math.PI + 360) % 360);
					saveToDb();
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
	 * HDEУ������
	 */
	public void HDEComp() {
		E = (float) (DELTA / 2 - priOrientation % DELTA);
		IController += StepDetectionUtil.getSign(E) * IC;
		if(STEPDETECTED) {
			if(SIGN != StepDetectionUtil.getSign(E)) IController = 0;
			orientation[0] = orientation[0] + IController;
			System.out.println("orientation: " + (orientation[0] * 180 / Math.PI + 360) % 360);
			matrix = StepDetectionUtil.getRotationMatrixFromOrientation(orientation[0], orientation[1], orientation[2]);
			STEPDETECTED = false;
			SIGN = StepDetectionUtil.getSign(E);
		}
	}
	
	/**
     * gyroFunction�����������ݻ��֣���ȡ�������ݣ�
     * ������������д��orientation
     * @param event �������¼�
     */
    public void gyroFunction(SensorEvent event) {
    	float[] deltaVector = new float[4];
        if(timestamp != 0) {
			final float dT = (event.timestamp - timestamp) * NS2S;
			System.arraycopy(event.values, 0, gyro, 0, 3);
			getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
        }
        
        timestamp = event.timestamp;
        
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);
        
		matrix = StepDetectionUtil.matrixMultiplication(matrix, deltaMatrix);
		SensorManager.getOrientation(matrix, orientation);
		slide_windows_ori[swPointer] = orientation;
    }
    
    private void getRotationVectorFromGyro(float[] gyroValues,
    		float[] deltaRotationVector,
    		float timeFactor) {
    	
    	float[] normValues = new float[3];
    	
    	// Calculate the angular speed of the sample
    	float omegaMagnitude = 
    			(float) Math.sqrt(gyroValues[0] * gyroValues[0] +
    					gyroValues[1] * gyroValues[1] +
    					gyroValues[2] * gyroValues[2]);
    	
    	// Normalize the rotation vector if it's big enough to get the axis
    	if(omegaMagnitude > EPSILON) {
    		normValues[0] = gyroValues[0] / omegaMagnitude;
    		normValues[1] = gyroValues[1] / omegaMagnitude;
    		normValues[2] = gyroValues[2] / omegaMagnitude;
    	}
    	
    	float thetaOvetTwo = omegaMagnitude * timeFactor;
    	float sinThetaOverTwo = (float) Math.sin(thetaOvetTwo);
    	float cosThetaOverTwo = (float) Math.cos(thetaOvetTwo);
    	deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
    	deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
    	deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
    	deltaRotationVector[3] = cosThetaOverTwo;
    }
    
    /**
     * ���Ų����ݱ��浽���ݿ���
     */
    private void saveToDb() {
    	Cursor c = db.query(TBL_NAME, null, null, null, null, null, null);
		
		double newlat = 0;
		double newlng = 0;
		if(c != null) {
			if(c.getCount() == 0) {
				newlat = StepDetectionUtil.getPoint(lat, lng, (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) strideLength)[0];
				newlng = StepDetectionUtil.getPoint(lat, lng, (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) strideLength)[1];
			}
			if(c.getCount() >=1) {
				c.moveToLast();
				System.out.println(c.getInt(1));
				newlat = StepDetectionUtil.getPoint(c.getDouble(5), c.getDouble(6), (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) strideLength)[0];
				newlng = StepDetectionUtil.getPoint(c.getDouble(5), c.getDouble(6), (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) strideLength)[1];
			}
		}
		
		ContentValues values = new ContentValues();
		values.put("length", strideLength);
		values.put("azimuth", (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360);
		values.put("pitch", (double) (slide_windows_ori[swPointer][1] * 180/Math.PI+ 360) % 360);
		values.put("roll", (double) (slide_windows_ori[swPointer][2] * 180/Math.PI+ 360) % 360);
		values.put("lat", newlat);
		values.put("lng", newlng);
		db.insert(TBL_NAME, null, values);
    }
	
}
