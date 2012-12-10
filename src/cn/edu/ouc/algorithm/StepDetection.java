/*
 * Copyright 2012 Ocean University of China.
 *
 */

package cn.edu.ouc.algorithm;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.Log;
import cn.edu.ouc.db.DatabaseHelper;
import cn.edu.ouc.preferences.IndoorTrackSettings;
import cn.edu.ouc.util.StepDetectionUtil;

/**
 * StepDetection�����ڽŲ�̽��.
 * ��̽�⵽�Ų�ʱ�����ûص�����StepTrigger,���������������ͷ�������������.
 * 
 * @author Chu Hongwei, Hong Feng
 */
public class StepDetection {

	private static final String TAG = StepDetection.class.getSimpleName();
	
	private StepTrigger st; // ʹ�ýӿ�StepTrigger���ⲿ���֪ͨ�Ų�̽�����
	
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
	private final static int CACHE = 35; // ��Ϊ�������ڵĻ���ʹ��
	// ��������ָ�룬ָʾ�洢λ�á�
	// ָ���CACHE����ʼ��ǰCACHE��λ����Ϊ�������ڵĻ���ʹ��
	private int swPointer = CACHE;
	private static final int BLOCKSIZE = 8; // ����1������0����ֵ
	private boolean firstStart = true; //�жϳ����Ƿ��״����У��Ա�Ի������ڵ���ʼλ�ý����趨
	private int stepCount; //̽��Ų���
	private double stepLength; //����
	
	
	/* ----------------------------------------------*/
	// ����gyroFunction�����Ĳ���
	public static final float EPSILON = 0.000000001f;
	private float[] gyro = new float[3]; // ����������
	private float timestamp;
    private static final float NS2S = 1.0f / 1000000000.0f; // ���뵽���ת��
    public float[] matrix = new float[9]; // ��ת����
    private float[] gyroOrientation = new float[3]; // �����ǲɼ��ķ���
    private float[][] slide_windows_ori; // �������ڣ����ڴ洢�����ǲɼ��ķ���
    
    /* ----------------------------------------------*/
	// ���ݿ������ز���
    DatabaseHelper mHelper;
	SQLiteDatabase db;
	private static final String TBL_NAME = "track_tbl";
	double lat = 36.16010; // ����
    double lng = 120.491951; // γ��
    
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
	 
	// �����趨
	SharedPreferences mSettings;
	IndoorTrackSettings mIndoorTrackSettings;
	
	// �㷨�趨
	private final static int MAGNETIC_BASED_ALGORITHM = 1;
	private final static int GYROSCOPE_BASED_ALGORITHM = 2;
	private final static int HDE_BASED_ALGORITHM = 3;
	
	// �ֻ�����λ���趨
	private final static int HAND_HELD = 1;
	private final static int TROUSER_POCKET = 2;
	
	// �������㷽ʽ�趨
	private final static boolean FIXED_STEP_LENGTH = true;
	
    
	/**
	 * ���캯��
	 * @param context
	 * @param stepTrigger �ӿڣ�����ʵ�ֻص�
	 * @param swSize �������ڴ�С
	 */
	public StepDetection(Context context, StepTrigger stepTrigger) {
		this.st = stepTrigger;
		mSettings = PreferenceManager.getDefaultSharedPreferences(context);
        mIndoorTrackSettings = new IndoorTrackSettings(mSettings);
        swSize = mIndoorTrackSettings.getSensitivity();
		this.slide_windows_acc = new float[swSize];
		this.localMeanAccel = new float[swSize];
		this.slide_windows_ori = new float[swSize][3];
		stepCount = 0;
		mHelper = new DatabaseHelper(context);
		db = mHelper.getWritableDatabase();
		
		matrix[0] = 1.0f; matrix[1] = 0.0f; matrix[2] = 0.0f;
		matrix[3] = 0.0f; matrix[4] = 1.0f; matrix[5] = 0.0f;
		matrix[6] = 0.0f; matrix[7] = 0.0f; matrix[8] = 1.0f;
		
		gyroOrientation[0] = 0.0f; gyroOrientation[1] = 0.0f; gyroOrientation[2] = 0.0f;
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
		        if(swPointer > swSize - 1) { // ���ָ��λ�ó������ڴ�С����ָ���Ƶ����봰����ʼλ��CACHE��
		        	swPointer = (swPointer % (swSize - 1)) + CACHE; // ���ڵ�ǰCACHE��λ����Ϊ����ʹ��
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
	@SuppressWarnings("deprecation")
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
		float threshold = StepDetectionUtil.getAverageLocalMeanAccel(localMeanAccel) + 0.5f;
		condition = StepDetectionUtil.getCondition(localMeanAccel, threshold);
		
		int numOne = 0; // ��¼�ж�����condition�У�����1�ĸ���
		int numZero = 0; // ��¼�ж�����condition�У�����0�ĸ���
		boolean flag = false; // ��¼��ǰ����1����0
		
		// ͨ��������1������0�ĸ����жϽŲ�
		for(int i = 0, j = 1; i < swSize - 1 && j < swSize - W; i++, j++) {
			if(firstStart) { // �״����г���ʱ���������ڵĳ�ʼλ������ΪCACHE��
				i = CACHE;   // ǰCACHE ����������
				j = i + 1;
			}
			firstStart = false;
			if(mIndoorTrackSettings.getAlgorithms() == HDE_BASED_ALGORITHM) {
				HDEComp(); // HDEУ������
			}
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
					if(!mIndoorTrackSettings.getStepLengthMode() == FIXED_STEP_LENGTH)
						{
						stepLength = StepDetectionUtil.getSL(0.33f, meanA);
						}
					else stepLength = mIndoorTrackSettings.getStepLength() / 100f;
					st.trigger(stepCount, (float) stepLength, gyroOrientation);
					if(mIndoorTrackSettings.getPhonePosition() == HAND_HELD) {
					priOrientation = (float) ((gyroOrientation[0] * 180 / Math.PI + 360) % 360);
					}
					else priOrientation = (float) ((gyroOrientation[2] * 180 / Math.PI + 360) % 360);
					saveToDb();
				}
			}
		}
		
		/* �������е����CACHE����������õ������ǰCACHEλ���У�
		 * ģ��ѭ�����С�
		*/
		for(int k = 0; k < CACHE; k++) {
			slide_windows_acc[k] = slide_windows_acc[k + swSize - CACHE];
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
			if(mIndoorTrackSettings.getPhonePosition() == HAND_HELD) {
				gyroOrientation[0] = gyroOrientation[0] + IController;
			}
			else gyroOrientation[2] = gyroOrientation[2] + IController;
			matrix = StepDetectionUtil.getRotationMatrixFromOrientation(gyroOrientation[0], gyroOrientation[1], gyroOrientation[2]);
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
		SensorManager.getOrientation(matrix, gyroOrientation);
		slide_windows_ori[swPointer] = gyroOrientation;
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
				if(mIndoorTrackSettings.getPhonePosition() == HAND_HELD) {
				newlat = StepDetectionUtil.getPoint(lat, lng, (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) stepLength)[0];
				newlng = StepDetectionUtil.getPoint(lat, lng, (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) stepLength)[1];
				}
				else {
					newlat = StepDetectionUtil.getPoint(lat, lng, (double) (slide_windows_ori[swPointer][2] * 180/Math.PI+ 360) % 360, (double) stepLength)[0];
					newlng = StepDetectionUtil.getPoint(lat, lng, (double) (slide_windows_ori[swPointer][2] * 180/Math.PI+ 360) % 360, (double) stepLength)[1];
				}
			}
			if(c.getCount() >=1) {
				c.moveToLast();
				if(mIndoorTrackSettings.getPhonePosition() == HAND_HELD) {
				newlat = StepDetectionUtil.getPoint(c.getDouble(5), c.getDouble(6), (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) stepLength)[0];
				newlng = StepDetectionUtil.getPoint(c.getDouble(5), c.getDouble(6), (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360, (double) stepLength)[1];
				}
				else {
					newlat = StepDetectionUtil.getPoint(c.getDouble(5), c.getDouble(6), (double) (slide_windows_ori[swPointer][2] * 180/Math.PI+ 360) % 360, (double) stepLength)[0];
					newlng = StepDetectionUtil.getPoint(c.getDouble(5), c.getDouble(6), (double) (slide_windows_ori[swPointer][2] * 180/Math.PI+ 360) % 360, (double) stepLength)[1];
				}
			}
		}
		
		ContentValues values = new ContentValues();
		values.put("length", stepLength);
		values.put("azimuth", (double) (slide_windows_ori[swPointer][0] * 180/Math.PI+ 360) % 360);
		values.put("pitch", (double) (slide_windows_ori[swPointer][1] * 180/Math.PI+ 360) % 360);
		values.put("roll", (double) (slide_windows_ori[swPointer][2] * 180/Math.PI+ 360) % 360);
		values.put("lat", newlat);
		values.put("lng", newlng);
		db.insert(TBL_NAME, null, values);
    }
	
}
