package com.mediatek.systemui.notification;

import android.content.Context;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ToastTest extends AndroidTestCase {

    private static final String TOAST_CONTENT_A = "toastContentA";
    private static final String TOAST_CONTENT_B = "toastContentB";
    Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
    }

    public void testMakeToast() {
        Toast toast = Toast.makeText(mContext, TOAST_CONTENT_A, Toast.LENGTH_LONG);
        View toastView = toast.getView();
        TextView textView = (TextView) toastView.findViewById(com.android.internal.R.id.message);
        String toastText = null;
        if (textView != null && !TextUtils.isEmpty(textView.getText())) {
            toastText = textView.getText().toString();
        }
        assertEquals(TOAST_CONTENT_A, toastText);
        toast.setText(TOAST_CONTENT_B);
        if (textView != null && !TextUtils.isEmpty(textView.getText())) {
            toastText = textView.getText().toString();
        }
        assertEquals(TOAST_CONTENT_B, toastText);
        assertEquals(Toast.LENGTH_LONG, toast.getDuration());
    }

    public void testCustomizedToastView() {
        Toast toast = new Toast(mContext);
        ViewGroup myView = new FrameLayout(mContext);
        Button button = new Button(mContext);
        TextView textView = new TextView(mContext);
        myView.addView(button, 0);
        myView.addView(textView, 1);
        toast.setView(myView);
        View resultView = toast.getView();
        assertTrue(resultView instanceof ViewGroup);
        assertTrue(((ViewGroup) resultView).getChildAt(0) instanceof Button);
        assertTrue(((ViewGroup) resultView).getChildAt(1) instanceof TextView);
    }
}
