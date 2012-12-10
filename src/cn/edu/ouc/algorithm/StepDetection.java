/*
 * Copyright 2012 Ocean University of China.
 *
 */

package cn.edu.ouc.algorithm;

import java.util.ArrayList;
import java.util.List;

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
	private float[] accel = new float[3]; // ˲ʱ���ٶ�
	private float[] orientation = new float[3]; // ˲ʱ�����Ʒ���
	private List<float[]> accelList = new ArrayList<float[]>(); // ���ٶ��б�
	private List<float[]> orientationList = new ArrayList<float[]>(); // �����Ʒ����б�
	private List<float[]> gyroOrientationList = new ArrayList<float[]>(); // �����Ƿ����б�
	private static final int W = 15; // �ֲ����ڴ�С�����ڼ���ֲ�ƽ�����ٶȺͷ���
	private int swSize; // �������ڴ�С
	
	private static final int BLOCKSIZE = 8; // ����1������0����ֵ
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
	private float IC = -0.0001f; // �̶���������ʾ������IController�Ĳ�������
	private static final float DELTA = 90f; // ���������Ƕȣ����������ഹֱ
	float IController = 0; // ����������������ƫ�����
	private int SIGN = 0; // �ж����߷���ƫ�����������һ�࣬SIGN = 1ƫ����࣬SIGN = 0ƫ���Ҳ�
	private double priOrientation = 0f; // ǰһ���ķ���
	private boolean STEPDETECTED = false; // �Ų�̽���־
	 
	// �����趨
	SharedPreferences mSettings;
	IndoorTrackSettings mIndoorTrackSettings;
	
	// �㷨�趨
	private final static int MAGNETIC_BASED_ALGORITHM = 1;
	private final static int GYROSCOPE_BASED_ALGORITHM = 2;
	private final static int HDE_BASED_ALGORITHM = 3;
	private final static int PSP_BASED_ALGORITHM = 4;
	
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
		stepCount = 0;
		mHelper = new DatabaseHelper(context);
		db = mHelper.getWritableDatabase();
		
		matrix[0] = 1.0f; matrix[1] = 0.0f; matrix[2] = 0.0f;
		matrix[3] = 0.0f; matrix[4] = 1.0f; matrix[5] = 0.0f;
		matrix[6] = 0.0f; matrix[7] = 0.0f; matrix[8] = 1.0f;
		
		gyroOrientation[0] = 0.0f; gyroOrientation[1] = 0.0f; gyroOrientation[2] = 0.0f;
		mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
	}
	
	/**
	 * ���������¼�
	 */
	public SensorEventListener mSensorEventListener = new SensorEventListener() {
		
		@SuppressWarnings("deprecation")
		@Override
		public void onSensorChanged(SensorEvent event) {
			switch(event.sensor.getType()) {
		    case Sensor.TYPE_ACCELEROMETER:
		    	System.arraycopy(event.values, 0, accel, 0, 3);
		        break;
		        
		    case Sensor.TYPE_GYROSCOPE:
		    	gyroFunction(event); // ��������������
		    	break;
		    	
		    case Sensor.TYPE_ORIENTATION:
		    	System.arraycopy(event.values, 0, orientation, 0, 3);
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
		
		mSensorManager.registerListener(mSensorEventListener, 
				mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
				SensorManager.SENSOR_DELAY_FASTEST);
	}
	
	/**
	 * ע��������
	 */
	public void stopSensor() {
		Log.i(TAG, "[StepDetection] stopSensor");
		mSensorManager.unregisterListener(mSensorEventListener);
		accelList.clear();
		gyroOrientationList.clear();
	}
	
	/**
	 * �Ų�̽���㷨���������ߵļ��ٶ������жϽŲ�
	 */
	private void checkForStep(List<float[]> orientationList) {
		Log.i(TAG, "[StepDetection] checkForStep");
		
		List<Float> magnitudeOfAccel = StepDetectionUtil.getMagnitudeOfAccel(accelList);
		List<Float> localMeanAccel = StepDetectionUtil.getLocalMeanAccel(magnitudeOfAccel, W);
		float threshold = StepDetectionUtil.getAverageLocalMeanAccel(localMeanAccel) + 0.5f;
		List<Integer> condition = StepDetectionUtil.getCondition(localMeanAccel, threshold);
		
		int numOne = 0; // ��¼�ж�����condition�У�����1�ĸ���
		int numZero = 0; // ��¼�ж�����condition�У�����0�ĸ���
		boolean flag = false; // ��¼��ǰ����1����0
		
		// ͨ��������1������0�ĸ����жϽŲ�
		for(int i = 0, j = 1; i < swSize - 1 && j < swSize - W; i++, j++) {
			flag = StepDetectionUtil.isOne(condition.get(i)); // �ж�ǰһ����������ж�����i�Ƿ�Ϊ1
			/* ���ǰһ��������i���ж������͵�ǰ������j���ж�������ͬ��
			 * ���Ҷ�����1����numOne��1. */
			if((condition.get(i) == condition.get(j)) && flag == true) 
			{				
				numOne++;
			}
			/* ���ǰһ��������i���ж������͵�ǰ������j���ж�������ͬ��
			 * ���Ҷ�����0����numZero��1. */
			if((condition.get(i) == condition.get(j)) && flag == false) 
			{
				numZero++;	
			}
			/* ���ǰһ��������i�����ڵ�ǰ������j��ֵ��
			 * ��������1������0�ĸ���������BLOCKSIZE����̽�⵽�Ų���
			 * ��numOne��numZero��0������̽��Ų��Ĳ����ͷ��� */
			if((condition.get(i) != condition.get(j)) && j > W && j < swSize - W) {
				if(numOne > BLOCKSIZE && numZero > BLOCKSIZE) {
					
					STEPDETECTED = true;
					stepCount++;
					float meanA = StepDetectionUtil.getMean(localMeanAccel, j, W);
					
					if(!mIndoorTrackSettings.getStepLengthMode() == FIXED_STEP_LENGTH)
						{
						stepLength = StepDetectionUtil.getSL(0.33f, meanA);
						}
					else stepLength = mIndoorTrackSettings.getStepLength() / 100f;
					
					double meanOrientation = 0;
					meanOrientation = StepDetectionUtil.getMeanOrientation(numOne, numZero, j, 
							orientationList, mIndoorTrackSettings.getPhonePosition(), mIndoorTrackSettings.getAlgorithms());
					st.trigger(stepCount, (float) stepLength, gyroOrientation);
					
					priOrientation = meanOrientation;
					System.out.println(priOrientation);
					
					saveToDb(meanOrientation);
					numOne = 0;
					numZero = 0;
				}
			}
		}
		
	}
	
	/**
	 * HDEУ������
	 */
	public void HDEComp() {
		if(stepCount < 2) {
			matrix[0] = 1.0f; matrix[1] = 0.0f; matrix[2] = 0.0f;
			matrix[3] = 0.0f; matrix[4] = 1.0f; matrix[5] = 0.0f;
			matrix[6] = 0.0f; matrix[7] = 0.0f; matrix[8] = 1.0f;
			IC = -0.0006f;
		}
		else IC = -0.0001f;
		
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
		
		if(mIndoorTrackSettings.getAlgorithms() == HDE_BASED_ALGORITHM || mIndoorTrackSettings.getAlgorithms() == PSP_BASED_ALGORITHM) {
			HDEComp(); // HDEУ������
		}
		
		float[] tempAccel = new float[3];
		System.arraycopy(accel, 0, tempAccel, 0, 3);
		accelList.add(tempAccel);
		
		float[] tempOrientation = new float[3];
		System.arraycopy(orientation, 0, tempOrientation, 0, 3);
		orientationList.add(tempOrientation);
		
		float[] tempGyroOrientation = new float[3];
		System.arraycopy(gyroOrientation, 0, tempGyroOrientation, 0, 3);
		gyroOrientationList.add(tempGyroOrientation);
		
		if(gyroOrientationList.size() > swSize) {
			if (mIndoorTrackSettings.getAlgorithms() != MAGNETIC_BASED_ALGORITHM) {
				checkForStep(gyroOrientationList);
			}
			else {
				checkForStep(orientationList);
			}
			for(int i = 0; i < swSize - 35; i++) {
				accelList.remove(0);
				orientationList.remove(0);
				gyroOrientationList.remove(0);
			}
		}
		
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
    private void saveToDb(double bearing) {
    	Cursor c = db.query(TBL_NAME, null, null, null, null, null, null);
		
		double newlat = 0;
		double newlng = 0;
		if(c != null) {
			if(c.getCount() == 0) {
				newlat = StepDetectionUtil.getPoint(lat, lng, bearing, (double) stepLength)[0];
				newlng = StepDetectionUtil.getPoint(lat, lng, bearing, (double) stepLength)[1];
			}
			if(c.getCount() >=1) {
				c.moveToLast();
				newlat = StepDetectionUtil.getPoint(c.getDouble(2), c.getDouble(3), bearing, (double) stepLength)[0];
				newlng = StepDetectionUtil.getPoint(c.getDouble(2), c.getDouble(3), bearing, (double) stepLength)[1];
			}
		}
		
		ContentValues values = new ContentValues();
		values.put("length", stepLength);
		values.put("lat", newlat);
		values.put("lng", newlng);
		db.insert(TBL_NAME, null, values);
    }
    
}
