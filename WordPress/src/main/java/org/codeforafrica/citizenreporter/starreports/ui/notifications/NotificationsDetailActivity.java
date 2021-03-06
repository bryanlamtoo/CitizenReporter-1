package org.codeforafrica.citizenreporter.starreports.ui.notifications;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.WindowManager;

import com.simperium.client.BucketObjectMissingException;

import org.codeforafrica.citizenreporter.starreports.GCMIntentService;
import org.codeforafrica.citizenreporter.starreports.R;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.codeforafrica.citizenreporter.starreports.models.AccountHelper;
import org.codeforafrica.citizenreporter.starreports.models.CommentStatus;
import org.codeforafrica.citizenreporter.starreports.models.Note;
import org.codeforafrica.citizenreporter.starreports.ui.ActivityLauncher;
import org.codeforafrica.citizenreporter.starreports.ui.WPWebViewActivity;
import org.codeforafrica.citizenreporter.starreports.ui.comments.CommentActions;
import org.codeforafrica.citizenreporter.starreports.ui.comments.CommentDetailFragment;
import org.codeforafrica.citizenreporter.starreports.ui.notifications.blocks.NoteBlockRangeType;
import org.codeforafrica.citizenreporter.starreports.ui.notifications.utils.SimperiumUtils;
import org.codeforafrica.citizenreporter.starreports.ui.reader.ReaderActivityLauncher;
import org.codeforafrica.citizenreporter.starreports.ui.reader.ReaderPostDetailFragment;
import org.codeforafrica.citizenreporter.starreports.ui.stats.StatsAbstractFragment;
import org.codeforafrica.citizenreporter.starreports.ui.stats.StatsActivity;
import org.codeforafrica.citizenreporter.starreports.ui.stats.StatsTimeframe;
import org.codeforafrica.citizenreporter.starreports.ui.stats.StatsViewAllActivity;
import org.codeforafrica.citizenreporter.starreports.ui.stats.StatsViewType;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.ToastUtils;

import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;

public class NotificationsDetailActivity extends ActionBarActivity implements
        CommentActions.OnNoteCommentActionListener {
    private static final String ARG_TITLE = "activityTitle";
    private static final String DOMAIN_WPCOM = "wordpress.com";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.i(AppLog.T.NOTIFS, "Creating NotificationsDetailActivity");

        setContentView(R.layout.notifications_detail_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setElevation(0.0f);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            String noteId = getIntent().getStringExtra(NotificationsListFragment.NOTE_ID_EXTRA);
            if (noteId == null) {
                showErrorToastAndFinish();
                return;
            }

            if (SimperiumUtils.getNotesBucket() != null) {
                try {
                    Note note = SimperiumUtils.getNotesBucket().get(noteId);

                    Map<String, String> properties = new HashMap<>();
                    properties.put("notification_type", note.getType());
                    AnalyticsTracker.track(AnalyticsTracker.Stat.NOTIFICATIONS_OPENED_NOTIFICATION_DETAILS, properties);

                    Fragment detailFragment = getDetailFragmentForNote(note);
                    getFragmentManager().beginTransaction()
                            .add(R.id.notifications_detail_container, detailFragment)
                            .commit();

                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(note.getTitle());
                    }

                    // mark the note as read if it's unread
                    if (note.isUnread()) {
                        // mark as read which syncs with simperium
                        note.markAsRead();
                        EventBus.getDefault().post(new NotificationEvents.NotificationsChanged());
                    }
                } catch (BucketObjectMissingException e) {
                    showErrorToastAndFinish();
                    return;
                }
            }
        } else if (savedInstanceState.containsKey(ARG_TITLE) && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(StringUtils.notNullStr(savedInstanceState.getString(ARG_TITLE)));
        }

        // Hide the keyboard, unless we arrived here from the 'Reply' action in a push notification
        if (!getIntent().getBooleanExtra(NotificationsListFragment.NOTE_INSTANT_REPLY_EXTRA, false)) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }

        GCMIntentService.clearNotificationsMap();
    }

    @Override
    public void finish() {
        super.finish();
        ActivityLauncher.slideOutToRight(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (getSupportActionBar() != null && getSupportActionBar().getTitle() != null) {
            outState.putString(ARG_TITLE, getSupportActionBar().getTitle().toString());
        }

        super.onSaveInstanceState(outState);
    }

    private void showErrorToastAndFinish() {
        AppLog.e(AppLog.T.NOTIFS, "Note could not be found.");
        ToastUtils.showToast(this, R.string.error_notification_open);
        finish();
    }

    /**
     * Tries to pick the correct fragment detail type for a given note
     * Defaults to NotificationDetailListFragment
     */
    private Fragment getDetailFragmentForNote(Note note) {
        if (note == null)
            return null;

        Fragment fragment;
        if (note.isCommentType()) {
            // show comment detail for comment notifications
            fragment = CommentDetailFragment.newInstance(note.getId());
        } else if (note.isAutomattcherType()) {
            // show reader post detail for automattchers about posts - note that comment
            // automattchers are handled by note.isCommentType() above
            boolean isPost = (note.getSiteId() != 0 && note.getPostId() != 0 && note.getCommentId() == 0);
            if (isPost) {
                fragment = ReaderPostDetailFragment.newInstance(note.getSiteId(), note.getPostId());
            } else {
                fragment = NotificationsDetailListFragment.newInstance(note.getId());
            }
        } else {
            fragment = NotificationsDetailListFragment.newInstance(note.getId());
        }

        return fragment;
    }

    public void showBlogPreviewActivity(long siteId) {
        if (isFinishing()) return;

        ReaderActivityLauncher.showReaderBlogPreview(this, siteId);
    }

    public void showPostActivity(long siteId, long postId) {
        if (isFinishing()) return;

        ReaderActivityLauncher.showReaderPostDetail(this, siteId, postId);
    }

    public void showStatsActivityForSite(int localTableSiteId, NoteBlockRangeType rangeType) {
        if (isFinishing()) return;

        if (rangeType == NoteBlockRangeType.FOLLOW) {
            Intent intent = new Intent(this, StatsViewAllActivity.class);
            intent.putExtra(StatsAbstractFragment.ARGS_VIEW_TYPE, StatsViewType.FOLLOWERS);
            intent.putExtra(StatsAbstractFragment.ARGS_TIMEFRAME, StatsTimeframe.DAY);
            intent.putExtra(StatsAbstractFragment.ARGS_SELECTED_DATE, "");
            intent.putExtra(StatsActivity.ARG_LOCAL_TABLE_BLOG_ID, localTableSiteId);
            intent.putExtra(StatsViewAllActivity.ARG_STATS_VIEW_ALL_TITLE, getString(R.string.stats_view_followers));
            startActivity(intent);
        } else {
            ActivityLauncher.viewBlogStats(this, localTableSiteId);
        }
    }

    public void showWebViewActivityForUrl(String url) {
        if (isFinishing() || url == null) return;

        if (url.contains(DOMAIN_WPCOM)) {
            WPWebViewActivity.openUrlByUsingWPCOMCredentials(this, url, AccountHelper.getDefaultAccount().getUserName());
        } else {
            WPWebViewActivity.openURL(this, url);
        }
    }

    public void showReaderPostLikeUsers(long blogId, long postId) {
        if (isFinishing()) return;

        ReaderActivityLauncher.showReaderLikingUsers(this, blogId, postId);
    }

    public void showReaderCommentsList(long siteId, long postId, long commentId) {
        if (isFinishing()) return;

        ReaderActivityLauncher.showReaderComments(this, siteId, postId, commentId);
    }

    @Override
    public void onModerateCommentForNote(Note note, CommentStatus newStatus) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(NotificationsListFragment.NOTE_MODERATE_ID_EXTRA, note.getId());
        resultIntent.putExtra(NotificationsListFragment.NOTE_MODERATE_STATUS_EXTRA, CommentStatus.toRESTString(newStatus));

        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
