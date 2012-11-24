package cn.edu.ouc.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import cn.edu.ouc.algorithm.StepDetection;
import cn.edu.ouc.algorithm.StepTrigger;

public class StepDetectionService extends Service implements StepTrigger {

	private static final String TAG = "StepDetectionService";

	private StepDetection mStepDetection;
	
	private int stepCount = 0;
	
	private float[] orientation = new float[3];
	
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
		mStepDetection = new StepDetection(this, this, 300);
		mStepDetection.startSensor();
		Toast.makeText(getBaseContext(), "StepDetection started", Toast.LENGTH_SHORT).show();
	}
	
	@Override
	public void onDestroy() {
		mStepDetection.stopSensor();
		super.onDestroy();
	}

	@Override
	public void trigger(float length, float[] orientation) {
		stepCount++;
		this.orientation = orientation;
	}
	
	// ��ȡ̽��Ų���
	public float getStep() {
		return orientation[0];
	}
}
