/*
 * Copyright (c) 2010-2011, 2013, AllSeen Alliance. All rights reserved.
 *
 *    Permission to use, copy, modify, and/or distribute this software for any
 *    purpose with or without fee is hereby granted, provided that the above
 *    copyright notice and this permission notice appear in all copies.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package org.alljoyn.bus.samples.simpleclient;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.*;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.alljoyn.bus.*;
import org.alljoyn.cops.peergroupmanager.PeerGroupListener;
import org.alljoyn.cops.peergroupmanager.PeerGroupManager;

public class Client extends Activity {
    /* Load the native alljoyn_java library. */
    static {
        System.loadLibrary("alljoyn_java");
    }
    
    private static final int MESSAGE_PING = 1;
    private static final int MESSAGE_PING_REPLY = 2;
    private static final int MESSAGE_POST_TOAST = 3;
    private static final int MESSAGE_START_PROGRESS_DIALOG = 4;
    private static final int MESSAGE_STOP_PROGRESS_DIALOG = 5;

    private static final String TAG = "SimpleClient";

    private EditText mEditText;
    private ArrayAdapter<String> mListViewArrayAdapter;
    private ListView mListView;
    private Menu menu;
    
    /* Handler used to make calls to AllJoyn methods. See onCreate(). */
    private BusHandler mBusHandler;
    
    private ProgressDialog mDialog;
    
    private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MESSAGE_PING:
                    String ping = (String) msg.obj;
                    mListViewArrayAdapter.add("Ping:  " + ping);
                    break;
                case MESSAGE_PING_REPLY:
                    String ret = (String) msg.obj;
                    mListViewArrayAdapter.add("Reply:  " + ret);
                    mEditText.setText("");
                    break;
                case MESSAGE_POST_TOAST:
                	Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_LONG).show();
                	break;
                case MESSAGE_START_PROGRESS_DIALOG:
                    mDialog = ProgressDialog.show(Client.this, 
                                                  "", 
                                                  "Finding Simple Service.\nPlease wait...", 
                                                  true,
                                                  true);
                    break;
                case MESSAGE_STOP_PROGRESS_DIALOG:
                    mDialog.dismiss();
                    break;
                default:
                    break;
                }
            }
        };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mListViewArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mListView = (ListView) findViewById(R.id.ListView);
        mListView.setAdapter(mListViewArrayAdapter);

        mEditText = (EditText) findViewById(R.id.EditText);
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_NULL
                        && event.getAction() == KeyEvent.ACTION_UP) {
                        /* Call the remote object's Ping method. */
                        Message msg = mBusHandler.obtainMessage(BusHandler.PING, 
                                                                view.getText().toString());
                        mBusHandler.sendMessage(msg);
                    }
                    return true;
                }
            });

        /* Make all AllJoyn calls through a separate handler thread to prevent blocking the UI. */
        HandlerThread busThread = new HandlerThread("BusHandler");
        busThread.start();
        mBusHandler = new BusHandler(busThread.getLooper());

        /* Connect to an AllJoyn object. */
        mBusHandler.sendEmptyMessage(BusHandler.CONNECT);
        mHandler.sendEmptyMessage(MESSAGE_START_PROGRESS_DIALOG);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        this.menu = menu;
        return true;
    }
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.quit:
	    	finish();
	        return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        /* Disconnect to prevent resource leaks. */
        mBusHandler.sendEmptyMessage(BusHandler.DISCONNECT);
    }
    
    /* This class will handle all AllJoyn calls. See onCreate(). */
    class BusHandler extends Handler {
		/*
		* Group prefix is handed to the Peer Group Manager's constructor
		* and used in discovery to find matching groups. A reverse URL
		* naming style is used.
		*/
		private static final String GROUP_PREFIX = "org.alljoyn.bus.samples.simple";
		private PeerGroupManager mPeerGroupManager;
		private SimpleInterface mSimpleInterface;
        
        /* These are the messages sent to the BusHandler from the UI. */
        public static final int CONNECT = 1;
        public static final int JOIN_SESSION = 2;
        public static final int DISCONNECT = 3;
        public static final int PING = 4;

        public BusHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            /* Connect to a remote instance of an object implementing the SimpleInterface. */
            case CONNECT: {
            	org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
				/*
				* PeerGroupManager handles all communication with AllJoyn.
				*
				* PeerGroupManager takes a group prefix, defined previously.
				*
				* Also, a PeerGroupListener is required to receive informative
				* callback methods. Only desired callback methods must be overridden. In
				* this client, we use the foundAdvertisedName and groupLost callback
				* methods.
				*
				* Finally, an array of BusObjects that you want the PeerGroupManager
				* to register with AllJoyn is passed in. This simple client is a pure
				* client with no bus objects, so a null reference is passed in.
				*/
				PeerGroupListener mPeerGroupListener = new PeerGroupListener()
				{
					@Override
					public void foundAdvertisedName(String groupName,
							short transport)
					{
						logInfo(String.format("MyPeerGroupListener.foundAdvertisedName" +
								"(%s, 0x%04x)", groupName, transport));
						/*
						* This client will only join the first service that
						* it sees advertising a group with the indicated
						* group prefix. If the program has already joined
						* a group (i.e. connected to a service) we will not
						* attempt to join another group.
						*
						* It is possible to join multiple groups,
						* however joining multiple groups is not shown
						* in this sample.
						*/
						Message msg = obtainMessage(JOIN_SESSION, groupName);
						sendMessage(msg);
					};

					@Override
					public void groupLost(String groupName)
					{
						/*
						* Upon losing a group advertisement, a message is sent to
						* start the process dialog in android.
						*/
						logInfo(String.format("MyPeerGroupListener.groupLost(%s)", groupName));
						mHandler.sendEmptyMessage(MESSAGE_START_PROGRESS_DIALOG);
					};
				};
				mPeerGroupManager = new PeerGroupManager(GROUP_PREFIX, mPeerGroupListener,	null);

                break;
            }
            case (JOIN_SESSION): {
				/*
				* To join a group, the only information needed is the group name,
				* obtained from the foundAdvertisedName callback method in this case.
				*/
				Status status = mPeerGroupManager.joinGroup((String) msg.obj);
				logStatus("PeerGroupManager.joinGroup() - groupName: " + (String) msg.obj, status);
				if (status == Status.OK) {
					/*
					* To communicate with a ProxyBusObject, we need the
					* BusObject's remote interface. Getting the interface
					* requires the Peer Id of the peer who owns the BusObjects,
					* the name of the group both you and the peer are in,
					* an object path descriptor, and the interface of the class.
					*
					* The service created the BusObject as well as the group,
					* and the service's Peer Id is obtained here by using
					* the getGroupHostPeerId method.
					*/
					mSimpleInterface = mPeerGroupManager.getRemoteObjectInterface(
							mPeerGroupManager.getGroupHostPeerId((String) msg.obj),
							(String) msg.obj, "/SimpleService", SimpleInterface.class);
					mHandler.sendEmptyMessage(MESSAGE_STOP_PROGRESS_DIALOG);
				}
				break;
            }
            
            /* Release all resources acquired in the connect. */
            case DISCONNECT: {
            	/*
				* PeerGroupManager has a cleanup method which unregisters bus objects and
				* disconnects from AllJoyn. The PeerGroupManager should no longer be used
				* after calling cleanup.
				*/
				mPeerGroupManager.cleanup();
				mBusHandler.getLooper().quit();
				break;
            }
            
            /*
             * Call the service's Ping method through the ProxyBusObject.
             *
             * This will also print the String that was sent to the service and the String that was
             * received from the service to the user interface.
             */
            case PING: {
                try {
                	if (mSimpleInterface != null) {
                		sendUiMessage(MESSAGE_PING, msg.obj);
                		String reply = mSimpleInterface.Ping((String) msg.obj);
                		sendUiMessage(MESSAGE_PING_REPLY, reply);
                	}
                } catch (BusException ex) {
                    logException("SimpleInterface.Ping()", ex);
                }
                break;
            }
            default:
                break;
            }
        }
        
        /* Helper function to send a message to the UI thread. */
        private void sendUiMessage(int what, Object obj) {
            mHandler.sendMessage(mHandler.obtainMessage(what, obj));
        }
    }

    private void logStatus(String msg, Status status) {
        String log = String.format("%s: %s", msg, status);
        if (status == Status.OK) {
            Log.i(TAG, log);
        } else {
        	Message toastMsg = mHandler.obtainMessage(MESSAGE_POST_TOAST, log);
            mHandler.sendMessage(toastMsg);
            Log.e(TAG, log);
        }
    }

    private void logException(String msg, BusException ex) {
        String log = String.format("%s: %s", msg, ex);
        Message toastMsg = mHandler.obtainMessage(MESSAGE_POST_TOAST, log);
        mHandler.sendMessage(toastMsg);
        Log.e(TAG, log, ex);
    }
    
    /*
     * print the status or result to the Android log. If the result is the expected
     * result only print it to the log.  Otherwise print it to the error log and
     * Sent a Toast to the users screen. 
     */
    private void logInfo(String msg) {
            Log.i(TAG, msg);
    }
}
