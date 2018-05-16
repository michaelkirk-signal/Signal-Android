package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SharedContactInjector;
import org.thoughtcrime.securesms.contactshare.SharedContactInjector.ResolvedContact;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.glide.KeyedInputStream;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.SharedContactSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.Util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SharedContactView extends LinearLayout implements SharedContactInjector.Target,
                                                               RecipientModifiedListener
{

  private ImageView avatarView;
  private TextView  nameView;
  private TextView  numberView;
  private TextView  actionButtonView;

  private SharedContactSlide sharedContactSlide;
  private Contact            contact;
  private Locale             locale;
  private GlideRequests      glideRequests;
  private EventListener      eventListener;

  private String imageKey;

  private final Map<String, Recipient> activeRecipients = new HashMap<>();

  public SharedContactView(Context context) {
    super(context);
    initialize();
  }

  public SharedContactView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SharedContactView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public SharedContactView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.shared_contact_view, this);

    avatarView       = findViewById(R.id.contact_avatar);
    nameView         = findViewById(R.id.contact_name);
    numberView       = findViewById(R.id.contact_number);
    actionButtonView = findViewById(R.id.contact_action_button);
  }

  public void setContact(@NonNull SharedContactSlide sharedContactSlide, @NonNull GlideRequests glideRequests, @NonNull Locale locale) {
    this.sharedContactSlide = sharedContactSlide;
    this.glideRequests      = glideRequests;
    this.locale             = locale;

    Stream.of(activeRecipients.values()).forEach(recipient ->  recipient.removeListener(this));
    this.activeRecipients.clear();

    SharedContactInjector.load(getContext(), sharedContactSlide, this);
  }

  public void setEventListener(@NonNull EventListener eventListener) {
    this.eventListener = eventListener;
  }

  public @NonNull View getAvatarView() {
    return avatarView;
  }

  @Override
  public void setResolvedContact(@Nullable ResolvedContact resolvedContact) {
    this.contact = resolvedContact != null ? resolvedContact.getContact() : null;

    if (resolvedContact != null) {
      presentContact(resolvedContact.getContact());
      presentAvatar(resolvedContact.getAvatarStream());
      presentActionButtons(resolvedContact.getRecipients());
    } else {
      clearView();
    }
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(() -> presentActionButtons(Collections.singletonList(recipient)));
  }

  private void presentContact(@Nullable Contact contact) {
    if (contact != null) {
      nameView.setText(ContactUtil.getDisplayName(contact));
      numberView.setText(ContactUtil.getDisplayNumber(contact, locale));
    } else {
      nameView.setText("");
      numberView.setText("");
    }
  }

  private void presentAvatar(@Nullable KeyedInputStream inputStream) {
    if (inputStream != null && !Util.equals(inputStream.getKey(), imageKey)) {
      imageKey = inputStream.getKey();
      glideRequests.load(inputStream)
                   .fallback(R.drawable.ic_contact_picture)
                   .circleCrop()
                   .diskCacheStrategy(DiskCacheStrategy.ALL)
                   .dontAnimate()
                   .into(avatarView);
    } else if (inputStream == null) {
      glideRequests.load(R.drawable.ic_contact_picture)
                   .circleCrop()
                   .diskCacheStrategy(DiskCacheStrategy.ALL)
                   .into(avatarView);
    }
  }

  private void presentActionButtons(@NonNull List<Recipient> recipients) {
    for (Recipient recipient : recipients) {
      activeRecipients.put(recipient.getAddress().serialize(), recipient);
    }

    List<Recipient> pushUsers   = new ArrayList<>(recipients.size());
    List<Recipient> systemUsers = new ArrayList<>(recipients.size());

    for (Recipient recipient : activeRecipients.values()) {
      recipient.addListener(this);

      if (recipient.getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
        pushUsers.add(recipient);
      } else if (recipient.isSystemContact()) {
        systemUsers.add(recipient);
      }
    }

    if (!pushUsers.isEmpty()) {
      actionButtonView.setText(R.string.SharedContactView_message);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onMessageClicked(pushUsers);
        }
      });
    } else if (!systemUsers.isEmpty()) {
      actionButtonView.setText(R.string.SharedContactView_invite_to_signal);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onInviteClicked(systemUsers);
        }
      });
    } else {
      actionButtonView.setText(R.string.SharedContactView_add_to_contacts);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null && contact != null) {
          eventListener.onAddToContactsClicked(sharedContactSlide, contact);
        }
      });
    }
  }

  private void clearView() {
    nameView.setText("");
    numberView.setText("");

    glideRequests.load(R.drawable.ic_contact_picture)
        .circleCrop()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(avatarView);
  }

  public interface EventListener {
    void onAddToContactsClicked(@NonNull SharedContactSlide sharedContactSlide, @NonNull Contact contact);
    void onInviteClicked(@NonNull List<Recipient> choices);
    void onMessageClicked(@NonNull List<Recipient> choices);
  }
}
