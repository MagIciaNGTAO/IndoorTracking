package cn.edu.ouc.ui;

import cn.edu.ouc.app.AppManager;
import android.app.Activity;
import android.os.Bundle;

/**
 * Ӧ�ó���Activity�Ļ���
 * @author will
 *
 */
public class BaseActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// ��Activity��ӵ���ջ
		AppManager.getAppManager().addActivity(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// ����Activity & �Ӷ����Ƴ�
		AppManager.getAppManager().finishActivity(this);
	}

}
