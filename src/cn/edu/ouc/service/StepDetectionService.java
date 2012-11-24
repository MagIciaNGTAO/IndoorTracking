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
	
	private static final int HAND_HELD = 1; // �ֳ�
	private static final int POCKET = 2; //�ڴ�
	
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
	public void trigger(int stepCount, float length, float[] orientation) {
		this.stepCount = stepCount;
		this.orientation = orientation;
	}
	
	// ��ȡ̽��Ų���
	public int getStep() {
		return stepCount;
	}
	
	// ��ȡ�н�����
	public float getHeading(int CARRY_MODEL) {
		switch(CARRY_MODEL) {
		case HAND_HELD:
			return (float) ((orientation[0] * 360 / Math.PI + 360) % 360); 
		case POCKET:
			return (float) ((orientation[1] * 360 / Math.PI + 360) % 360); 
		default:
			return (float) ((orientation[0] * 360 / Math.PI + 360) % 360); 
		}
		
	}
}
