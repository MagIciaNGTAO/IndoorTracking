package cn.edu.ouc.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import cn.edu.ouc.R;
import cn.edu.ouc.ui.HomeActivity;

/**
 * Ӧ�ó��������ࣺ��ʾ��ӭ���沢��ת��������
 * @author will
 */
public class AppStart extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final View view = View.inflate(this, R.layout.start, null);
		setContentView(view);
		
		// ����չʾ������
		AlphaAnimation aa = new AlphaAnimation(0.3f, 1.0f);
		aa.setDuration(0);
		view.startAnimation(aa);
		aa.setAnimationListener(new AnimationListener() {
			
			@Override
			public void onAnimationStart(Animation animation) {
				
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
				
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				redirectTo();
			}
		});
	}
	
	/**
	 * ��ת��...
	 */
	private void redirectTo() {
		Intent intent = new Intent(this, HomeActivity.class);
		startActivity(intent);
		finish();
	}

	
}
