package org.elastos.carrier;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.elastos.carrier.robot.RobotProxy;
import org.elastos.carrier.common.TestOptions;
import org.elastos.carrier.common.Synchronizer;
import org.elastos.carrier.exceptions.ElastosException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FriendMessageTest {
	private static final String TAG = "FriendRequestTest";
	private static Carrier carrierInst;
	private static TestOptions options;
	private static TestHandler handler;
	private static RobotProxy robotProxy;
	private static String robotId;

	private static Context getAppContext() {
		return InstrumentationRegistry.getTargetContext();
	}

	private static String getAppPath() {
		return getAppContext().getFilesDir().getAbsolutePath();
	}

	static class TestHandler extends AbstractCarrierHandler {
		Synchronizer synch = new Synchronizer();

		String from;
		String msgBody;

		public void onReady(Carrier carrier) {
			synch.wakeup();
		}

		public void onFriendAdded(Carrier carrier, FriendInfo info) {
			synch.wakeup();
		}

		public void onFriendRemoved(Carrier carrier, String friendId) {
			synch.wakeup();
		}

		public void onFriendMessage(Carrier whisper, String from, String message) {
			this.msgBody = message;
			this.from = from.split("@")[0];
			Log.i(TAG, "this.from " + this.from);
			synch.wakeup();
		}
	}

	static class TestReceiver implements RobotProxy.RobotIdReceiver {
		private Synchronizer synch = new Synchronizer();

		@Override
		public void onReceived(String userId) {
			robotId = userId;
			synch.wakeup();
		}
	}

	@BeforeClass
	public static void setUp() {
		options = new TestOptions(getAppPath());
		handler = new TestHandler();

		try {
			TestReceiver receiver = new TestReceiver();
			robotProxy = RobotProxy.getRobot(getAppContext());
			robotProxy.bindRobot(receiver);
			receiver.synch.await();

			carrierInst = Carrier.getInstance(options, handler);
			carrierInst.start(1000);
			handler.synch.await();
		} catch (ElastosException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@AfterClass
	public static void tearDown() {
		try {
			carrierInst.kill();
			robotProxy.unbindRobot();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testSendMeMessage() {
		try {
			String hello = "test send me message";
			carrierInst.sendFriendMessage(carrierInst.getUserId(), hello);
			handler.synch.await();

			assertEquals(handler.from, carrierInst.getUserId());
			assertEquals(handler.msgBody, hello);

		} catch (Exception e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	private void removeFriendAnyWay(String userId) {
		try {
			if (carrierInst.isFriend(userId)) {
				carrierInst.removeFriend(userId);
				handler.synch.await();
			}
		} catch (ElastosException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testSendStrangeAMessage() {
		removeFriendAnyWay(robotId);

		try {
			carrierInst.sendFriendMessage(robotId, TAG);
		} catch (ElastosException e) {
			assertEquals(e.getErrorCode(), 0x8100000b);
			Log.i(TAG, "errcode: " +  e.getErrorCode());
			assertTrue(true);
		}
	}

	private void makeFriendAnyWay(String userId) {
		try {
			if (!carrierInst.isFriend(userId)) {
				carrierInst.addFriend(robotId, "auto confirmed");
				handler.synch.await(); // for friend request reply.
				handler.synch.await(); // for friend added.
			}
		} catch (ElastosException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

	@Test
	public void testSendFriendMessage() {
		makeFriendAnyWay(robotId);

		try {
			String hello = "test send friend message";
			carrierInst.sendFriendMessage(robotId, TAG);
			robotProxy.waitForMessageArrival();

			robotProxy.tellRobotSendMeMessage(carrierInst.getUserId(), hello);
			handler.synch.await();

			assertEquals(handler.from, robotId);
			assertEquals(handler.msgBody, hello);
		} catch (ElastosException e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}
}