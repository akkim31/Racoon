package com.suzei.racoon.ui.chatroom.group;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dinuscxj.refresh.RecyclerRefreshLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.makeramen.roundedimageview.RoundedImageView;
import com.squareup.picasso.Picasso;
import com.suzei.racoon.R;
import com.suzei.racoon.ui.auth.StartActivity;
import com.suzei.racoon.ui.base.Contract;
import com.suzei.racoon.ui.group.GroupActivity;
import com.suzei.racoon.ui.group.MembersActivity;
import com.suzei.racoon.ui.chatroom.messagelist.MessagesAdapter;
import com.suzei.racoon.ui.chatroom.messagelist.MessagePresenter;
import com.suzei.racoon.ui.chatroom.ChatContract;
import com.suzei.racoon.ui.group.GroupDetailsPresenter;
import com.suzei.racoon.model.Groups;
import com.suzei.racoon.util.OnlineStatus;
import com.suzei.racoon.view.EmptyRecyclerView;
import com.suzei.racoon.util.TakePicture;
import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiPopup;

import java.util.ArrayList;

import butterknife.BindColor;
import butterknife.BindDrawable;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

public class GroupChatActivity extends AppCompatActivity implements
        Contract.DetailsView<Groups>,
        ChatContract.ChatView,
        Contract.AdapterView<MessagesAdapter> {

    public static final String EXTRA_GROUP_ID = "group_id";

    private GroupDetailsPresenter groupDetailsPresenter;
    private GroupChatPresenter groupChatPresenter;
    private MessagePresenter messagePresenter;

    private EmojiPopup emojiPopup;
    private TakePicture takePicture;

    private DatabaseReference mUserChatRef;
    private DatabaseReference mMessageRef;
    private StorageReference mMessageStorage;

    private Groups groups;
    private String groupId;
    private String currentUserId;
    private ArrayList<String> members;
    private ArrayList<String> admins;

    @BindColor(android.R.color.white) int whiteColor;
    @BindDrawable(R.drawable.back) Drawable drawableBack;
    @BindView(R.id.group_chat_room_root_view) View rootView;
    @BindView(R.id.chat_room_toolbar) Toolbar toolbar;
    @BindView(R.id.chat_room_toolbar_image) RoundedImageView toolbarImageView;
    @BindView(R.id.chat_room_emoji) ImageButton emojiButtonView;
    @BindView(R.id.chat_room_camera) ImageButton cameraView;
    @BindView(R.id.chat_room_send) ImageButton sendView;
    @BindView(R.id.chat_room_name) TextView nameView;
    @BindView(R.id.chat_room_recycler_refresh_layout) RecyclerRefreshLayout refreshLayout;
    @BindView(R.id.chat_room_list) EmptyRecyclerView listMessagesView;
    @BindView(R.id.chat_room_empty) LinearLayout emptyLayout;
    @BindView(R.id.chat_room_image) RoundedImageView imageView;
    @BindView(R.id.chat_room_message_input) EmojiEditText mesageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);
        initBundle();
        initObjects();
        setUpToolbar();
        setUpEmojiKeyboard();
        setUpTakePick();
        setUpRecyclerView();
        setUpListeners();
    }

    private void initBundle() {
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
    }

    private void initObjects() {
        ButterKnife.bind(this);
        groupDetailsPresenter = new GroupDetailsPresenter(this);
        messagePresenter = new MessagePresenter(this);

        currentUserId = FirebaseAuth.getInstance().getUid();
        groupChatPresenter = new GroupChatPresenter(this);
        mUserChatRef = FirebaseDatabase.getInstance().getReference()
                .child("user_chats").child(currentUserId).child(groupId);
        mMessageRef = FirebaseDatabase.getInstance().getReference()
                .child("messages").child(currentUserId).child(groupId);
        mMessageStorage = FirebaseStorage.getInstance().getReference().child("messages")
                .child(groupId).child("pictures").child(String.valueOf(System.currentTimeMillis()));
    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeAsUpIndicator(drawableBack);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    private void setUpEmojiKeyboard() {
        emojiPopup = EmojiPopup.Builder.fromRootView(rootView)
                .setOnEmojiPopupShownListener(() ->
                        emojiButtonView.setImageResource(R.drawable.keyboard))
                .setOnEmojiPopupDismissListener(() ->
                        emojiButtonView.setImageResource(R.drawable.emoji))
                .build(mesageView);
    }

    private void setUpTakePick() {
        takePicture = new TakePicture(this, this, new TakePicture.ImageListener() {

            @Override
            public void onEmojiPick(String image) {
                groupChatPresenter.sendGroupMessage(
                        groupId,
                        "image",
                        members,
                        image);
            }

            @Override
            public void onCameraGalleryPick(byte[] imageByte) {
                UploadTask uploadTask = mMessageStorage.putBytes(imageByte);
                takePicture.uploadThumbnailToStorage(mMessageStorage, uploadTask, image_url ->
                        groupChatPresenter.sendGroupMessage(
                                groupId,
                                "image",
                                members,
                                image_url));

            }

        });
    }

    private void setUpRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        listMessagesView.setLayoutManager(layoutManager);
        listMessagesView.setEmptyView(emptyLayout);
    }

    private void setUpListeners() {
        messagePresenter.start(currentUserId, groupId);
        refreshLayout.setOnRefreshListener(() -> {
            messagePresenter.loadMore();
            refreshLayout.setRefreshing(false);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        takePicture.onActivityResult(requestCode, resultCode, data);
    }

    @OnClick(R.id.chat_room_emoji)
    public void onEmojiClick() {
        emojiPopup.toggle();
    }

    @OnClick(R.id.chat_room_camera)
    public void onCameraClick() {
        takePicture.showPicker();
    }

    @OnClick(R.id.chat_room_send)
    public void onSendClick() {
        String message = mesageView.getText().toString().trim();
        ArrayList<String> members =  new ArrayList<>(groups.getMembers().keySet());

        if (TextUtils.isEmpty(message)) {
            return;
        }

        sendView.setEnabled(false);

        groupChatPresenter.sendGroupMessage(
                groupId,
                "text",
                members,
                message);
    }

    @Override
    protected void onStop() {
        super.onStop();
        groupDetailsPresenter.destroy(groupId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        OnlineStatus.set(false);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        OnlineStatus.set(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        messagePresenter.destroy();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(GroupChatActivity.this, StartActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            groupDetailsPresenter.showGroupDetails(groupId);
            groupChatPresenter.seenMessage(groupId);
        }
    }

    @Override
    public void showProgress() {

    }

    @Override
    public void hideProgress() {

    }

    @Override
    public void onLoadSuccess(Groups groups) {
        this.groups = groups;
        members = new ArrayList<>(groups.getMembers().keySet());
        admins = new ArrayList<>(groups.getAdmin().keySet());
        nameView.setText(groups.getName());
        Picasso.get().load(groups.getImage()).fit().centerCrop().into(imageView);
        Picasso.get().load(groups.getImage()).fit().centerCrop().into(toolbarImageView);
    }

    @Override
    public void onLoadFailed(String message) {

    }

    @Override
    public void sendSuccess() {
        sendView.setEnabled(true);
        mesageView.setText("");
    }

    @Override
    public void sendFailed() {
        sendView.setEnabled(true);
    }

    @Override
    public void setAdapter(MessagesAdapter adapter) {

        if (adapter != null) {
            listMessagesView.setAdapter(adapter);
        } else {
            Timber.i("Adapter is null");
        }

    }

    @Override
    public void loadFailed() {
        Timber.i("Adapter is null");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.group_chat_options, menu);
        for(int i = 0; i < menu.size(); i++){
            Drawable drawable = menu.getItem(i).getIcon();
            if(drawable != null) {
                drawable.mutate();
                drawable.setColorFilter(whiteColor, PorterDuff.Mode.SRC_ATOP);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                finish();
                return true;

            case R.id.menu_delete_conv:
                deleteConversation();
                return true;

            case R.id.menu_view_members:
                viewMembers();
                return true;

            case R.id.menu_add_members:
                addMembers();
                return true;

            case R.id.menu_manage_group:
                // Only admin can access this
                manageGroup();
                return true;

            default:
                throw new IllegalArgumentException("Invalid menu id = " + item.getItemId());
        }
    }

    private void deleteConversation() {
        mUserChatRef.removeValue();
        mMessageRef.removeValue();
        finish();
        Toast.makeText(this, "Conversation Deleted" , Toast.LENGTH_SHORT).show();
    }

    private void viewMembers() {
        Bundle args = new Bundle();
        args.putString(MembersActivity.EXTRA_ID, groupId);
        args.putInt(MembersActivity.EXTRA_MEMBERS_TYPE, MembersActivity.MembersType.VIEW_MEMBERS);

        Intent membersIntent = new Intent(this, MembersActivity.class);
        membersIntent.putExtras(args);
        startActivity(membersIntent);
        Timber.i("Group Id= %s", groupId);
    }

    private void addMembers() {
        Bundle args = new Bundle();
        args.putString(MembersActivity.EXTRA_ID, groupId);
        args.putInt(MembersActivity.EXTRA_MEMBERS_TYPE, MembersActivity.MembersType.ADD_MEMBERS);
        args.putStringArrayList(MembersActivity.EXTRA_MEMBERS, members);

        Intent membersIntent = new Intent(this, MembersActivity.class);
        membersIntent.putExtras(args);
        startActivity(membersIntent);
        Timber.i("Group Id= %s", groupId);
    }

    private void manageGroup() {
        if (!admins.contains(currentUserId)) {
            Toast.makeText(this, "Only admin users can manage the group",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent groupIntent = new Intent(this, GroupActivity.class);
        groupIntent.putExtra(GroupActivity.EXTRA_ID, groupId);
        startActivity(groupIntent);
        Timber.i("Group Id= %s", groupId);
    }
}
