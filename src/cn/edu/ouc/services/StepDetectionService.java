package cn.edu.ouc.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import cn.edu.ouc.algorithm.StepDetection;
import cn.edu.ouc.algorithm.StepTrigger;

public class StepDetectionService extends Service implements StepTrigger {

	private static final String TAG = "StepDetectionService";

	private StepDetection mStepDetection;
	
	private float distance;
	
	SharedPreferences sharedPreferences;
	
	// �ͻ�ͨ��mBinder�ͷ������ͨ��
	private final IBinder mBinder = new StepDetectionBinder();
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	/**
     * ͨѶ�࣬���ںͿͻ��˰󶨡�  
     * ��ΪStepDetectionService����ͻ�������ͬһ�����̣����Բ���ҪIPC��
     */
	public class StepDetectionBinder extends Binder {
		public StepDetectionService getService() {
			// ����StepDetectionServiceʵ���������ͻ��Ϳ��Ե��÷���Ĺ�������
			return StepDetectionService.this;
		}
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "[StepDetectionService] onCreate");
		super.onCreate();
		distance = 0;
		sharedPreferences = getSharedPreferences("stepInfo", Context.MODE_PRIVATE);
		
		mStepDetection = new StepDetection(this, this);
		startSensor();
	}
	
	@Override
	public void onDestroy() {
		stopSensor();
		super.onDestroy();
	}
	
	/**
	 * ���ô�����
	 */
	public void startSensor() {
		mStepDetection.startSensor();
	}
	
	/**
	 * ֹͣ������
	 */
	public void stopSensor() {
		mStepDetection.stopSensor();
	}

	@Override
	public void trigger(int stepCount, float stepLength, float[] orientation) {
		distance += stepLength;
		Editor editor = sharedPreferences.edit();
		editor.putInt("stepCount", stepCount);
		editor.putFloat("stepLength", stepLength);
		editor.putFloat("distance", distance);
		editor.putFloat("yaw", orientation[0]);
		editor.putFloat("pitch", orientation[1]);
		editor.putFloat("roll", orientation[2]);
		editor.commit();
	}
	
}
