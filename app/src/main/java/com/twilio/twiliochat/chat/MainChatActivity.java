package com.twilio.twiliochat.chat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.twilio.chat.Channel;
import com.twilio.chat.ChatClient;
import com.twilio.chat.ChatClientListener;
import com.twilio.chat.ErrorInfo;
import com.twilio.chat.StatusListener;
import com.twilio.chat.User;
import com.twilio.twiliochat.R;
import com.twilio.twiliochat.application.AlertDialogHandler;
import com.twilio.twiliochat.application.SessionManager;
import com.twilio.twiliochat.application.TwilioChatApplication;
import com.twilio.twiliochat.chat.channels.ChannelAdapter;
import com.twilio.twiliochat.chat.channels.ChannelManager;
import com.twilio.twiliochat.chat.channels.LoadChannelListener;
import com.twilio.twiliochat.chat.listeners.InputOnClickListener;
import com.twilio.twiliochat.chat.listeners.TaskCompletionListener;
import com.twilio.twiliochat.landing.LoginActivity;

import java.util.List;

public class MainChatActivity extends AppCompatActivity implements ChatClientListener {
  private Context context;
  private Activity mainActivity;
  private Button logoutButton;
  private Button addChannelButton;
  private TextView usernameTextView;
  private ChatClientManager chatClientManager;
  private ListView channelsListView;
  private ChannelAdapter channelAdapter;
  private ChannelManager channelManager;
  private MainChatFragment chatFragment;
  private DrawerLayout drawer;
  private ProgressDialog progressDialog;
  private MenuItem leaveChannelMenuItem;
  private MenuItem deleteChannelMenuItem;
  private SwipeRefreshLayout refreshLayout;

  //new
  private Button switchToPr;
  private Button switchToPu;

  @Override
  protected void onDestroy() {
    super.onDestroy();

    //added for debugging
    //leaveCurrentChannel();
    //SessionManager.getInstance().logoutUser();
    //did not work

    new Handler().post(new Runnable() {
      @Override
      public void run() {
        chatClientManager.shutdown();
        TwilioChatApplication.get().getChatClientManager().setChatClient(null);
      }
    });
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main_chat);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
    ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
        R.string.navigation_drawer_open, R.string.navigation_drawer_close);
    drawer.setDrawerListener(toggle);
    toggle.syncState();

    refreshLayout = (SwipeRefreshLayout) findViewById(R.id.refreshLayout);

    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();

    chatFragment = new MainChatFragment();
    fragmentTransaction.add(R.id.fragment_container, chatFragment);
    fragmentTransaction.commit();

    context = this;
    mainActivity = this;
    logoutButton = (Button) findViewById(R.id.buttonLogout);
    addChannelButton = (Button) findViewById(R.id.buttonAddChannel);
    usernameTextView = (TextView) findViewById(R.id.textViewUsername);
    channelsListView = (ListView) findViewById(R.id.listViewChannels);

    //new
    switchToPr = (Button) findViewById(R.id.switchPrivate);
    switchToPu = (Button) findViewById(R.id.switchPublic);
    //
    channelManager = ChannelManager.getInstance();
    setUsernameTextView();

    setUpListeners();
    checkTwilioClient();
  }

  private void setUpListeners() {
    logoutButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        promptLogout();
      }
    });
    addChannelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showAddChannelDialog();
      }
    });
    refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
      @Override
      public void onRefresh() {
        refreshLayout.setRefreshing(true);
        refreshChannels();
      }
    });
    channelsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        setChannel(position);
      }
    });

    //new for switching to private
    switchToPr.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        switchToprivate();
      }
    });

    //new switching to public
    switchToPu.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        refreshChannels();
      }
    });
  }

  //new function to view private channels
  public void switchToprivate(){
    channelManager.populateUserChannels(new LoadChannelListener() {
      @Override
      public void onChannelsFinishedLoading(final List<Channel> channels) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            channelAdapter.setChannels(channels);
            refreshLayout.setRefreshing(false);
          }
        });
      }
    });
  }

  @Override
  public void onBackPressed() {
    if (drawer.isDrawerOpen(GravityCompat.START)) {
      drawer.closeDrawer(GravityCompat.START);
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main_chat, menu);
    this.leaveChannelMenuItem = menu.findItem(R.id.action_leave_channel);
    this.leaveChannelMenuItem.setVisible(false);
    this.deleteChannelMenuItem = menu.findItem(R.id.action_delete_channel);
    this.deleteChannelMenuItem.setVisible(false);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    if (id == R.id.action_leave_channel) {
      leaveCurrentChannel();
      return true;
    }
    if (id == R.id.action_delete_channel) {
      promptChannelDeletion();
    }

    return super.onOptionsItemSelected(item);
  }

  private String getStringResource(int id) {
    Resources resources = getResources();
    return resources.getString(id);
  }

  private void refreshChannels() {
    channelManager.populateChannels(new LoadChannelListener() {
      @Override
      public void onChannelsFinishedLoading(final List<Channel> channels) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            channelAdapter.setChannels(channels);
            refreshLayout.setRefreshing(false);
          }
        });
      }
    });
  }

  private void populateChannels() {
    channelManager.setChannelListener(this);
    channelManager.populateChannels(new LoadChannelListener() {
      @Override
      public void onChannelsFinishedLoading(List<Channel> channels) {
        channelAdapter = new ChannelAdapter(mainActivity, channels);
        channelsListView.setAdapter(channelAdapter);
        MainChatActivity.this.channelManager
            .joinOrCreateGeneralChannelWithCompletion(new StatusListener() {
              @Override
              public void onSuccess() {
                runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    channelAdapter.notifyDataSetChanged();
                    stopActivityIndicator();
                    setChannel(0);
                  }
                });
              }

              @Override
              public void onError(ErrorInfo errorInfo) {
                showAlertWithMessage(getStringResource(R.string.generic_error));
              }
            });
      }
    });
  }

  private void setChannel(final int position) {
    List<Channel> channels = channelManager.getChannels();
    if (channels == null) {
      return;
    }

    final Channel currentChannel = chatFragment.getCurrentChannel();
    final Channel selectedChannel = channels.get(position);

    //added to invite agent
    selectedChannel.getMembers().inviteByIdentity("agent1", new StatusListener() {
      @Override
      public void onSuccess() {
        Log.d(TwilioChatApplication.TAG,"User Invited!");
      }
    });
    //

    if (currentChannel != null && currentChannel.getSid().contentEquals(selectedChannel.getSid())) {
      drawer.closeDrawer(GravityCompat.START);
      return;
    }
    hideMenuItems(position);
    if (selectedChannel != null) {
      showActivityIndicator("Joining " + selectedChannel.getFriendlyName() + " channel");
      if (currentChannel != null && currentChannel.getStatus() == Channel.ChannelStatus.JOINED) {
        this.channelManager.leaveChannelWithHandler(currentChannel, new StatusListener() {
          @Override
          public void onSuccess() {
            joinChannel(selectedChannel);
          }

          @Override
          public void onError(ErrorInfo errorInfo) {
            stopActivityIndicator();
          }
        });
        return;
      }
      joinChannel(selectedChannel);
      stopActivityIndicator();
    } else {
      stopActivityIndicator();
      showAlertWithMessage(getStringResource(R.string.generic_error));
      Log.e(TwilioChatApplication.TAG,"Selected channel out of range");
    }
  }

  private void joinChannel(final Channel selectedChannel) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        chatFragment.setCurrentChannel(selectedChannel, new StatusListener() {
          @Override
          public void onSuccess() {
            MainChatActivity.this.stopActivityIndicator();
          }

          @Override
          public void onError(ErrorInfo errorInfo) {
          }
        });
        setTitle(selectedChannel.getFriendlyName());
        drawer.closeDrawer(GravityCompat.START);
      }
    });
  }

  private void hideMenuItems(final int position) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        MainChatActivity.this.leaveChannelMenuItem.setVisible(position != 0);
        MainChatActivity.this.deleteChannelMenuItem.setVisible(position != 0);
      }
    });
  }

  private void showAddChannelDialog() {
    String message = getStringResource(R.string.new_channel_prompt);
    AlertDialogHandler.displayInputDialog(message, context, new InputOnClickListener() {
      @Override
      public void onClick(String input) {
        if (input.length() == 0) {
          showAlertWithMessage(getStringResource(R.string.channel_name_required_message));
          return;
        }
        createChannelWithName(input);
      }
    });
  }

  private void createChannelWithName(String name) {
    name = name.trim();
    if (name.toLowerCase()
        .contentEquals(this.channelManager.getDefaultChannelName().toLowerCase())) {
      showAlertWithMessage(getStringResource(R.string.channel_name_equals_default_name));
      return;
    }
    //changed type to private in createChannelWithName
    this.channelManager.createChannelWithName(name, new StatusListener() {
      @Override
      public void onSuccess() {
        switchToprivate();
      }

      @Override
      public void onError(ErrorInfo errorInfo) {
        showAlertWithMessage(getStringResource(R.string.generic_error));
      }
    });
  }

  private void promptChannelDeletion() {
    String message = getStringResource(R.string.channel_delete_prompt_message);
    AlertDialogHandler.displayCancellableAlertWithHandler(message, context,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            deleteCurrentChannel();
          }
        });
  }

  private void deleteCurrentChannel() {
    Channel currentChannel = chatFragment.getCurrentChannel();
    channelManager.deleteChannelWithHandler(currentChannel, new StatusListener() {
      @Override
      public void onSuccess() {
      }
      @Override
      public void onError(ErrorInfo errorInfo) {
        showAlertWithMessage(getStringResource(R.string.message_deletion_forbidden));
      }
    });
  }

  private void leaveCurrentChannel() {
    final Channel currentChannel = chatFragment.getCurrentChannel();
    if (currentChannel.getStatus() == Channel.ChannelStatus.NOT_PARTICIPATING) {
      setChannel(0);
      return;
    }
    channelManager.leaveChannelWithHandler(currentChannel, new StatusListener() {
      @Override
      public void onSuccess() {
        setChannel(0);
      }

      @Override
      public void onError(ErrorInfo errorInfo) {
        stopActivityIndicator();
      }
    });
  }

  private void checkTwilioClient() {
    showActivityIndicator(getStringResource(R.string.loading_channels_message));
    chatClientManager = TwilioChatApplication.get().getChatClientManager();
    if (chatClientManager.getChatClient() == null) {
      initializeClient();
    } else {
      populateChannels();
    }
  }

  private void initializeClient() {
    chatClientManager.connectClient(new TaskCompletionListener<Void, String>() {
      @Override
      public void onSuccess(Void aVoid) {
        populateChannels();
      }

      @Override
      public void onError(String errorMessage) {
        stopActivityIndicator();
        showAlertWithMessage("Client connection error: " + errorMessage);
      }
    });
  }

  private void promptLogout() {
    final String message = getStringResource(R.string.logout_prompt_message);
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        AlertDialogHandler.displayCancellableAlertWithHandler(message, context,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {

                //debug
                leaveCurrentChannel();
                //worked!!!

                SessionManager.getInstance().logoutUser();
                showLoginActivity();
              }
            });
      }
    });

  }

  private void showLoginActivity() {
    Intent launchIntent = new Intent();
    launchIntent.setClass(getApplicationContext(), LoginActivity.class);
    startActivity(launchIntent);
    finish();
  }

  private void setUsernameTextView() {
    String username =
        SessionManager.getInstance().getUserDetails().get(SessionManager.KEY_USERNAME);
    usernameTextView.setText(username);
  }

  private void stopActivityIndicator() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (progressDialog.isShowing()) {
          progressDialog.dismiss();
        }
      }
    });
  }

  private void showActivityIndicator(final String message) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        progressDialog = new ProgressDialog(MainChatActivity.this.mainActivity);
        progressDialog.setMessage(message);
        progressDialog.show();
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
      }
    });
  }

  private void showAlertWithMessage(final String message) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        AlertDialogHandler.displayAlertWithMessage(message, context);
      }
    });
  }



  @Override
  public void onChannelAdded(Channel channel) {
    Log.d(TwilioChatApplication.TAG,"Channel Added");
    refreshChannels();
  }

  @Override
  public void onChannelDeleted(final Channel channel) {
    Log.d(TwilioChatApplication.TAG,"Channel Deleted");
    Channel currentChannel = chatFragment.getCurrentChannel();
    if (channel.getSid().contentEquals(currentChannel.getSid())) {
      chatFragment.setCurrentChannel(null, null);
      setChannel(0);
    }
    refreshChannels();
  }

  //implemented for invite to private chat
  @Override
  public void onChannelInvited(final Channel channel) {

    //implemented to add agent to the support channel
    channel.join(new StatusListener() {
      @Override
      public void onSuccess() {
        //extra step to revert him to join channel
        joinChannel(channel);
        switchToprivate();
        //
        Log.d(TwilioChatApplication.TAG, "Joined Channel: " + channel.getFriendlyName());
      }
    });
  }

  @Override
  public void onChannelSynchronizationChange(Channel channel) {

  }

  @Override
  public void onError(ErrorInfo errorInfo) {

  }

  @Override
  public void onClientSynchronization(ChatClient.SynchronizationStatus synchronizationStatus) {

  }

  @Override
  public void onConnectionStateChange(ChatClient.ConnectionState connectionState) {

  }

  @Override
  public void onTokenExpired() {

  }

  @Override
  public void onTokenAboutToExpire() {

  }

  @Override
  public void onChannelJoined(Channel channel) {

  }

  @Override
  public void onChannelUpdated(Channel channel, Channel.UpdateReason updateReason) {

  }

  @Override
  public void onUserUpdated(User user, User.UpdateReason updateReason) {

  }

  @Override
  public void onUserSubscribed(User user) {

  }

  @Override
  public void onUserUnsubscribed(User user) {

  }

  @Override
  public void onNewMessageNotification(String s, String s1, long l) {

  }

  @Override
  public void onAddedToChannelNotification(String s) {

  }

  @Override
  public void onInvitedToChannelNotification(String s) {

  }

  @Override
  public void onRemovedFromChannelNotification(String s) {

  }

  @Override
  public void onNotificationSubscribed() {

  }

  @Override
  public void onNotificationFailed(ErrorInfo errorInfo) {

  }
}
