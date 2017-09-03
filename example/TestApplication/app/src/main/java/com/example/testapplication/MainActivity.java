package com.example.testapplication;

import android.app.Activity;
import android.os.Bundle;

import com.testfairy.TestFairy;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		TestFairy.begin(this, "e27cf8c46bb25d8986e21915d700e493b268df0b"); 
	}
}
