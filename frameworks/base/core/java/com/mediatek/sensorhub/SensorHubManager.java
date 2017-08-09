package com.mediatek.sensorhub;

import java.util.ArrayList;
import java.util.List;

import android.os.Build;
import android.os.RemoteException;
import android.util.Log;


/**
 * SensorHubManager allows you to configure an action to be performed when some conditions are met.
 * <p/>
 * The action is represented by an {@link Action} instance, which wraps an android.app.PendingIntent instance in it.
 * The real actions, such as starting an activity or sending a broadcast, are described by the PendingInent instance.
 * <p/>
 * The condition is represented by a {@link Condition} instance. It is based on the virtual sensor data.
 * The virtual sensor data are a set of facts or circumstances that surround a situation or event, not a simple data.
 * The virtual sensor is also called context here, so the virtual sensor data are also called context data.
 * <p/>
 * The device is allowed to enter suspend mode after configuring actions.
 * The suspend mode is a low-power mode under which the application processor is not powered.
 * The device will be waken up to perform the action when the action's associated condition is met.
 * Therefore, all actions configured by this class are low power consumption since the device can
 * switch to suspend mode and be waken up when needed.
 * <p/>
 *
 * SensorHubManager also allows you to configure your customized gesture to google's
 * unspecified gesture.
 * <p/>
 * The CGesture represents that supported gesture type, such as shake(ContextInfo.Shake),
 * facedown(ContextInfo.Facing), pickup(ContextInfo.Pickup), tap(ContextInfo.Tap),
 * snapshot(ContextInfo.Snapshot) and other customized gesture types.
 * <p/>
 * Google had defined some gestures which the actual gesture is not specified, this API can
 * help you to config your customized gesture to google's unspecified gesture.
 * For example, if you config a shake gesture to google's TYPE_WAKE_GESTURE, when shake occures
 * that it will wake up the device
 * which behaves as if the power button was pressed, turning the screen on.
 * <p/>
 * It also has default mapping relationship if not set.
 * <p/>
 * <h2>Configure low power actions:</h2>
 * There is an inner framework running on the sensor hub device,
 * no matter the device is in suspend mode or not.
 * It does the following things:
 * <br/>1. Polling raw sensor data, computing out context data based on them
 * then putting the raw data and context data into a pool.
 * <br/>2. Checking out whether any condition set up by application is met base on the pool data.
 * <br/>3. Performing the action if its related condition is met; otherwise, doing nothing.
 * <p/>
 * For example, when the application is to start the {@code SubActivity} when the device is in vehicle and the confidence>=80,
 * it can configure the action as the following code does:
 * <code><pre class="prettyprint">
 * public class ActivityRecognition extends Activity {
 *
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.layout.activity_recognition);
 *     }
 *
 *     protected void onResume() {
 *         super.onResume();
 *         requestAction();
 *     }
 *
 *     private void requestAction() {
 *         SensorHubManager manager = (SensorHubManager)getSystemService(SensorHubManager.SENSORHUB_SERVICE);
 *         int contextType = ContextInfo.Type.USER_ACTIVITY;
 *         if (manager.isContextSupported(contextType)) {
 *             //create a action based on a PendingIntent that will start SubActivity
 *             Intent intent = new Intent(this, SubActivity.class);
 *             PendingIntent callbackIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
 *             Action action = new Action(callbackIntent, true);
 *
 *             //create a condition representing the device is in vehicle and the confidence >= 80
 *             Condition.Builder builder = new Condition.Builder();
 *             Condition stateCondition = builder.createCondition(
 *                     ContextInfo.UserActivity.CURRENT_STATE,
 *                     Condition.OP_EQUALS,
 *                     ContextInfo.UserActivity.State.IN_VEHICLE);
 *             Condition confidenceCondition = builder.createCondition(
 *                     ContextInfo.UserActivity.CONFIDENCE,
 *                     Condition.OP_GREATER_THAN_OR_EQUALS,
 *                     80);
 *             Condition combinedCondition = builder.combineWithAnd(stateCondition, confidenceCondition);
 *
 *             //request the action to be performed when the condition is met
 *             int requestId = manager.requestAction(combinedCondition, action);
 *             if (requestId < 0) {
 *                 Log.e("ActivityRecognition", "requestAction failed! errorCode=" + requestId);
 *             }
 *         }
 *     }
 * }
 * </pre></code>
 * <p/>
 * When the value of {@link ContextInfo.UserActivity#CURRENT_STATE} is {@link ContextInfo.UserActivity.State#IN_VEHICLE},
 * and the value of {@link ContextInfo.UserActivity#CONFIDENCE} is more than or equals to 80,
 * the {@code send(android.content.Context, int, android.content.Intent)} method of PendingIntent instance of the action
 * will be called with the intent parameter contains the ActionDataResult in its extras.
 * <p/>
 * Here is the example to show you how to extract ActionDataResult from the intent
 * and how to cancel a action when it is not needed:
 * <code><pre class="prettyprint">
 * public class SubActivity extends Activity {
 *
 *     private int mRequestId;
 *
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.layout.activity_sub);
 *     }
 *
 *     protected void onResume() {
 *         super.onResume();
 *         handleActivityRecognitionResult();
 *     }
 *
 *     protected void onDestroy() {
 *         super.onDestroy();
 *         cancelAction();
 *     }
 *
 *     private void handleActivityRecognitionResult() {
 *         Intent intent = getIntent();
 *         if (ActionDataResult.hasResult(intent)) {
 *             ActionDataResult result = ActionDataResult.extractResult(intent);
 *             mRequestId = result.getRequestId();
 *             List<DataCell> datalist = result.getData();
 *             int dataSize = datalist.size();
 *             for (int i = 0; i < dataSize; i++) {
 *                 DataCell data = datalist.get(i);
 *                 if (data.getIndex() == ContextInfo.UserActivity.CURRENT_ACTIVITY) {
 *                     assert(data.getType() == DataCell.DATA_TYPE_INT);
 *                     int activity = data.getIntValue();
 *                     // Do work with activity value
 *                 } else if (data.getIndex() == ContextInfo.UserActivity.CONFIDENCE) {
 *                     assert(data.getType() == DataCell.DATA_TYPE_INT);
 *                     int confidence = data.getIntValue();
 *                     // Do work with confidence value
 *                 }
 *             }
 *         }
 *     }
 *
 *     private void cancelAction() {
 *         if (mRequestId != 0) {
 *             SensorHubManager manager = (SensorHubManager)getSystemService(SensorHubManager.SENSORHUB_SERVICE);
 *             boolean result = manager.cancelAction(mRequestId);
 *             if (!result) {
 *                 Log.e("SubActivity", "Failed to cancel request with id " + mRequestId);
 *             }
 *         }
 *     }
 * }
 * </pre></code>
 * <p class="note">
 * Note: The device will be waken up from suspend mode to perform the action when its associated condition is met.
 * Make sure to cancel unnecessary actions to enable significant power savings by preventing the device from
 * waken up to perform the unnecessary action.
 *
 * <br/>
 * The SensorHub feature is not available on some platforms. Make sure the feature is available before using
 * its classes.<br/>
 * The following example shows you how to check whether the feature is available:
 *
 * <pre><code>
 *     private void setupAction() {
 *         // check out whether the SensorHub feature is available
 *         if (SensorHubSupport.isSensorHubFeatureAvailable()) {
 *             SensorHubManager manager = (SensorHubManager)getSystemService(SensorHubManager.SENSORHUB_SERVICE);
 *             // Do works...
 *         }
 *     }
 * </code></pre>
 * </p>
 *
 * <h2>Configure google's unspecified gesture:</h2>
 * <p/>
 * Here is the example to show you how to configure one customized gesture to google's
 * unspecified gesture.
 * <p/>
 * <code><pre class="prettyprint">
 * public class SubActivity extends Activity implements SensorEventListener {
 *
 *  private SensorHubManager mSensorHubManager;
 *  private SensorManager mSensorManager;
 *  private Sensor mWakeupSensor;
 *
 *  protected void onCreate(Bundle savedInstanceState) {
 *      super.onCreate(savedInstanceState);
 *      setContentView(R.layout.activity_sub);
 *  }
 *
 *  public SubActivity() {
 *      mSensorHubManager = (SensorHubManager) getSystemService(SensorHubManager.SENSORHUB_SERVICE);
 *      mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
 *      mWakeupSensor = mSensorManager
 *              .getDefaultSensor(Sensor.TYPE_WAKE_GESTURE);
 *  }
 *
 *  protected void onResume() {
 *      super.onResume();
 *      addCongifurableGesture();
 *      //Note trigger sensor after addCongifurableGesture.
 *      mSensorManager.requestTriggerSensor(this, mWakeupSensor);
 *  }
 *
 *  protected void onPause() {
 *      super.onPause();
 *      cancelConfigurableGesture();
 *      //Note cancel trigger sensor after cancelConfigurableGesture.
 *      mSensorManager.cancelTriggerSensor(this, mWakeupSensor);
 *  }
 *
 *  private addCongifurableGesture() {
 *      int contextType = ContextInfo.Type.SHAKE;
 *      if (mSensorHubManager.isCGestureSupported(contextType)) {
 *          mSensorHubManager.addConfigurableGesture(ContextInfo.Shake.VALUE,
 *                  Sensor.TYPE_WAKE_GESTURE);
 *      }
 *  }
 *
 *  private cancelConfigurableGesture() {
 *      mSensorHubManager.cancelConfigurableGesture(ContextInfo.Shake.VALUE,
 *              Sensor.TYPE_WAKE_GESTURE);
 *  }
 *
 *  public void onAccuracyChanged(Sensor sensor, int accuracy) {
 *      // TODO
 *  }
 *
 *  public void onSensorChanged(SensorEvent event) {
 *      // TODO
 *  }
 *}
 * </pre></code>
 * <p/>
 *
 * @see Condition
 * @see Action
 *
 */


public class SensorHubManager implements ISensorHubManager {
    private static final String TAG = "SensorHubManager";

    private static final boolean LOG = !"user".equals(Build.TYPE) && !"userdebug".equals(Build.TYPE);

    private ISensorHubService mService;
    private List<Integer> mContextList;
    private List<Integer> mGestureContextList = new ArrayList<>();

    /**
     * Allows an application to wake up the device when it is in suspend mode through sensor hub.
     *
     * @hide
     */
    public static String WAKE_DEVICE_SENSORHUB = "com.mediatek.permission.WAKE_DEVICE_SENSORHUB";

    /**
     * Allows an application to update sensor hub action.
     *
     * @hide
     */
    public static String UPDATE_SENSORHUB_ACTION = "com.mediatek.permission.UPDATE_SENSORHUB_ACTION";

    /**
     * Extra key for request ID in an intent.
     */
    public static String EXTRA_REQUEST_ID = "com.mediatek.sensorhub.EXTRA_REQUEST_ID";

    /**
     * A constant to describe the error code is unknown.
     */
    public static final int REQUEST_ERROR_UNKNOWN = -1;

    /**
     * A constant to describe the error code has no resource.
     */
    public static final int REQUEST_ERROR_NO_RESOURCE = -2;

    /**
     * A constant to describe the error code is invalid context.
     */
    public static final int REQUEST_ERROR_CONTEXT_INVALID = -3;

    /**
     * Constructs a SensorHubManager instance.
     *
     * @param service The SensorHubService instance.
     *
     * @hide
     */
    public SensorHubManager(ISensorHubService service) {
        mService = service;
    }

    /**
     * Gets the list of available context types.
     *
     * @return The list of contexts that are available on this platform.
     */
    @Override
    public List<Integer> getContextList() {
        List<Integer> list = null;
        if (mContextList == null && mService != null) {
            try {
                mContextList = mService.getContextList().toList();
            } catch (RemoteException e) {
                Log.e(TAG, "getContextList: RemoteException!", e);
            }
        }
        if (mContextList != null) {
            list = new ArrayList<Integer>(mContextList);
        }
        if (LOG) {
            Log.v(TAG, "getContextList: list=" + list);
        }
        return list;
    }

    /**
     * Checks whether the specified context type is supported.
     *
     * @param type The context type to be checked. It should be one of {@code ContextInfo.Type.*}.
     *
     * @return {@code true} if the type is supported, {@code false} otherwise.
     *
     * @see com.mediatek.sensorhub.ContextInfo.Type
     */
    @Override
    public boolean isContextSupported(int type) {
        List<Integer> types = getContextList();
        if (null == types) {
            if (LOG) Log.w(TAG, "isContextSupported: null context list!");
            return false;
        }
        return types.contains(type);
    }

    /**
     * Requests a specified action with the condition.
     *
     * <ul>
     * <li>Non-repeatable action: When the condition is met,
     * the action will be performed once, and it will be cancelled automatically.
     * To continue triggering this action, the application must request the action again.
     * </li>
     * <li>Repeatable action: When the condition is met, the action will be performed and will not be cancelled.
     * Always make sure you cancel the request that is not needed.
     * Failing to do so can drain the battery in just a few hours if the action is performed from time to time.
     * </li>
     * </ul>
     *
     * <p class="note">Requires "com.mediatek.permission.WAKE_DEVICE_SENSORHUB" permission.
     *
     * @param condition The condition that will trigger the action when it is met.
     * @param action The action to be performed when the condition is met.
     *
     * @return The unique request ID if success. Otherwise, one of {@code REQUEST_ERROR_*}.
     *
     * @see #cancelAction(int)
     */
    @Override
    public int requestAction(Condition condition, Action action) {
        if (condition == null || action == null) {
            Log.e(TAG, "requestAction: failed! condition=" + condition + ", action=" + action);
            return REQUEST_ERROR_UNKNOWN;
        }
        int rid = REQUEST_ERROR_UNKNOWN;
        if (mService != null) {
            try {
                rid = mService.requestAction(condition, action);
            } catch (RemoteException e) {
                Log.e(TAG, "requestAction: RemoteException!", e);
            }
        }
        if (LOG) {
            Log.v(TAG, "requestAction: condition=" + condition + ", action[" + action.isRepeatable()
                + "," + action.isOnConditionChanged() + "], rid=" + rid);
        }
        return rid;
    }

    /**
     * Updates the condition of the action by request ID.
     *
     * <p class="note">Requires "com.mediatek.permission.WAKE_DEVICE_SENSORHUB" permission.
     *
     * @param requestId The ID of the request to be updated.
     * @param condition The new condition that will override the old one.
     *
     * @return {@code true} if the condition is updated successfully, {@code false} otherwise.
     */
    @Override
    public boolean updateCondition(int requestId, Condition condition) {
        if (requestId <= 0 || condition == null) {
            Log.e(TAG, "updateCondition: failed! rid=" + requestId + ", condition=" + condition);
            return false;
        }

        boolean result = false;
        if (mService != null) {
            try {
                result = mService.updateCondition(requestId, condition);
            } catch (RemoteException e) {
                Log.e(TAG, "updateCondition: RemoteException! rid=" + requestId + ",condition=" + condition, e);
            }
        }
        if (LOG) {
            Log.v(TAG, "updateCondition: rid=" + requestId + ", condition=" + condition + (result ? " succeed." : " failed!"));
        }
        return result;
    }

    /**
     * Cancels the specified action by the request ID.
     *
     * <p/>
     * Normally, the requested action will be cancelled automatically when the condition is met and its repeatable is set to false.
     * Otherwise, you must use this method to cancel the specified action.
     * <p/>
     *
     * @param requestId The ID of the request to be cancelled. It was returned by {@link #requestAction(Condition, Action)}.
     *
     * @return {@code true} if the action is cancelled successfully, {@code false} otherwise.
     *
     * @see #requestAction(Condition, Action)
     */
    @Override
    public boolean cancelAction(int requestId) {
        boolean success = false;
        if (mService != null && requestId > 0) {
            try {
                success = mService.cancelAction(requestId);
            } catch (RemoteException e) {
                Log.e(TAG, "cancelAction: RemoteException! rid=" + requestId, e);
            }
        }
        if (LOG) {
            Log.v(TAG, "cancelAction: rid=" + requestId + (success ? " succeed." : "failed!"));
        }
        return success;
    }

    /**
      * Gets the list of available configurable gesture context types.
      *
      * @return the list of configurable gesture contexts that are available on this platform.
      *
      */
   @Override
     public List<Integer> getCGestureList() {
       int cgestureNumber = 4;
         List<Integer> types = getContextList();
         int cgesturessdata[] = { //should sync with ContextInfo.Type
             ContextInfo.Type.FACING,
             ContextInfo.Type.PICK_UP,
             ContextInfo.Type.SHAKE,
             //ContextInfo.Type.TAP,
             //ContextInfo.Type.TWIST,
             ContextInfo.Type.SNAPSHOT,
         };
         for (int i = 0; i < cgestureNumber; i++) {
            if (types != null && types.contains(cgesturessdata[i])) {
                mGestureContextList.add(cgesturessdata[i]);
                if (LOG) Log.v(TAG, "cgesture contexts[" + i + "]=" + cgesturessdata[i]);
            } else {
                if (LOG) Log.v(TAG, "cgesture list is null");
            }
       }
         return mGestureContextList;
    }

   /**
    * Checks whether the specified configurable gesture type is supported.
    *
    * @param type The gesture type to be checked. It should be one of {@code ContextInfo.Type.*}.
    *
    * @return {@code true} if the type is supported, {@code false} otherwise.
    *
    */
    @Override
    public boolean isCGestureSupported(int type) {
        List<Integer> types = getCGestureList();
        if (null == types) {
            if (LOG) Log.v(TAG, "isCGestureSupported: null context list!");
                return false;
            }
        return types.contains(type);
    }

     /**
    * Configure the configurable gesture to google gesture.
    *
    * <p>Set the gesture type which defined by yourself to google's gesture.
    *
    * @param cgesture Configurable gesture type data slot index.
    * The cgesture should be one of shake(ContextInfo.Shake.VALUE),
    * facedown(ContextInfo.Facing.FACE_DOWN),
    * pickup(ContextInfo.Pickup.VALUE), tap(ContextInfo.Tap.VALUE),
    * snapshot(ContextInfo.Snapshot.VALUE)
    * or other customized gesture types.
    *
    * @param ggesture Google gesture type.
    * The ggesture can be google's undefined gesture, such as TYPE_WAKE_GESTURE,
    * TYPE_GLANCE_GESTURE, TYPE_PICK_UP_GESTURE.
    * <div class="note">Registration it after using this.</div>
    *
    */
   @Override
   public void addConfigurableGesture(int cgesture, int ggesture) {
       if (mService != null) {
           try {
               if (LOG) {
                   Log.v(TAG, "addConGesture: cgesture=" + cgesture + ", ggesture=" + ggesture);
               }
               mService.addConGesture(cgesture, ggesture);
           } catch (RemoteException e) {
               Log.e(TAG, "addConGesture: RemoteException! cgesture=" + cgesture + ", ggesture=" +
               ggesture);
           }
        }
   }

     /**
    * Cancel configure the configurable gesture to google gesture.
    *
    * <p>Cancel the configurable gesture to google's gesture mapping relationship.
    *
    * @param cgesture Configurable gesture type data slot index.
    * The cgesture should be one of shake(ContextInfo.Shake.VALUE),
    * facedown(ContextInfo.Facing.FACE_DOWN),
    * pickup(ContextInfo.Pickup.VALUE), tap(ContextInfo.Tap.VALUE),
    * snapshot(ContextInfo.Snapshot.VALUE)
    * or other customized gesture types.
    * @param ggesture Google gesture type.
    * The ggesture can be google's undefined gesture, such as TYPE_WAKE_GESTURE,
    * TYPE_GLANCE_GESTURE, TYPE_PICK_UP_GESTURE.
    * <div class="note">Unregistration it after using this.</div>
    *
    */
   @Override
   public void cancelConfigurableGesture(int cgesture, int ggesture) {
       if (mService != null) {
           try {
               if (LOG) {
                   Log.v(TAG, "cancelConGesture: cgesture=" + cgesture + ", ggesture=" + ggesture);
               }
               mService.cancelConGesture(cgesture, ggesture);
           } catch (RemoteException e) {
               Log.e(TAG, "cancelConGesture: RemoteException! cgesture=" + cgesture + ", ggesture="
               + ggesture);
           }
        }
   }

    /**
     * Enables the wake up gesture detection thread.
     *
     * <p>Do not enable gesture detection thread when unnecessary for the it will consume powers.
     *
     * <p class="note">Requires "com.mediatek.permission.WAKE_DEVICE_SENSORHUB" permission.
     *
     * @param enabled {@code true} to enable it, {@code false} to disable it.
     *
     * @return {@code true} if it is enabled successfully, {@code false} otherwise.
     *
     * @hide
     */
    @Override
    public boolean enableGestureWakeup(boolean enabled) {
        boolean result = false;
        if (mService != null) {
            try {
                result = mService.enableGestureWakeup(enabled);
            } catch (RemoteException e) {
                Log.e(TAG, "enableTouchGestureWakeup: RemoteException! enable=" + enabled, e);
            }
        }
        if (LOG) {
            Log.v(TAG, "enableTouchGestureWakeup: enable=" + enabled +
            (result ? " succeed." : " failed!"));
        }

        return false;
    }

}
