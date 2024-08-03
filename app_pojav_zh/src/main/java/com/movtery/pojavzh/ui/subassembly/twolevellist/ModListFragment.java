package com.movtery.pojavzh.ui.subassembly.twolevellist;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.movtery.pojavzh.ui.fragment.FragmentWithAnim;
import com.movtery.pojavzh.utils.anim.AnimUtils;
import com.movtery.pojavzh.utils.ZHTools;
import com.movtery.pojavzh.utils.anim.ViewAnimUtils;
import com.movtery.pojavzh.utils.stringutils.StringUtils;

import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public abstract class ModListFragment extends FragmentWithAnim {
    protected FragmentActivity activity;
    private RecyclerView.Adapter<?> parentAdapter = null;
    private RecyclerView mRecyclerView;
    private View mModsLayout, mOperateLayout, mLoadeingView;
    private TextView mNameText, mSelectTitle, mFailedToLoad;
    private ImageView mIcon;
    private ImageButton mBackToTop;
    private Button mReturnButton, mRefreshButton;
    private CheckBox mReleaseCheckBox;
    private Future<?> currentTask;
    private boolean releaseCheckBoxVisible = true;

    public ModListFragment() {
        super(R.layout.fragment_mod_download);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindViews(view);
        init();

        ViewAnimUtils.slideInAnim(this);
    }

    protected void init() {
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(requireContext());
        mRecyclerView.setLayoutAnimation(new LayoutAnimationController(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_downwards)));
        mRecyclerView.setLayoutManager(layoutManager);

        mRefreshButton.setOnClickListener(v -> refreshTask());
        mReleaseCheckBox.setOnClickListener(v -> refreshTask());
        mReturnButton.setOnClickListener(v -> {
            if (parentAdapter != null) {
                hideParentElement(false);
                mRecyclerView.setAdapter(parentAdapter);
                mRecyclerView.scheduleLayoutAnimation();
                parentAdapter = null;
            } else {
                ZHTools.onBackPressed(requireActivity());
            }
        });

        mBackToTop.setOnClickListener(v -> mRecyclerView.smoothScrollToPosition(0));

        refreshTask();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.activity = requireActivity();
    }

    @Override
    public void onPause() {
        cancelTask();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        cancelTask();
        super.onDestroy();
    }

    private void hideParentElement(boolean visible) {
        cancelTask(); //中断当前正在执行的任务

        mRefreshButton.setClickable(!visible);
        mReleaseCheckBox.setClickable(!visible);

        AnimUtils.setVisibilityAnim(mSelectTitle, visible);
        AnimUtils.setVisibilityAnim(mRefreshButton, !visible);

        if (releaseCheckBoxVisible) AnimUtils.setVisibilityAnim(mReleaseCheckBox, !visible);
    }

    private void cancelTask() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
    }

    private void refreshTask() {
        currentTask = refresh();
    }

    protected abstract Future<?> refresh();

    protected void componentProcessing(boolean state) {
        AnimUtils.setVisibilityAnim(mLoadeingView, state);
        mRecyclerView.setVisibility(state ? View.GONE : View.VISIBLE);

        mRefreshButton.setClickable(!state);
        mReleaseCheckBox.setClickable(!state);
    }

    private void bindViews(View view) {
        mModsLayout = view.findViewById(R.id.mods_layout);
        mOperateLayout = view.findViewById(R.id.operate_layout);

        mRecyclerView = view.findViewById(R.id.zh_mod);
        mBackToTop = view.findViewById(R.id.zh_mod_back_to_top);
        mLoadeingView = view.findViewById(R.id.zh_mod_loading);
        mIcon = view.findViewById(R.id.zh_mod_icon);
        mNameText = view.findViewById(R.id.zh_mod_name);
        mSelectTitle = view.findViewById(R.id.zh_select_title);
        mFailedToLoad = view.findViewById(R.id.zh_mod_failed_to_load);

        mReturnButton = view.findViewById(R.id.zh_mod_return_button);
        mRefreshButton = view.findViewById(R.id.zh_mod_refresh_button);
        mReleaseCheckBox = view.findViewById(R.id.zh_mod_release_version);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                if (layoutManager != null && adapter != null) {
                    int firstPosition = layoutManager.findFirstVisibleItemPosition();
                    boolean b = firstPosition >= adapter.getItemCount() / 3;

                    AnimUtils.setVisibilityAnim(mBackToTop, b);
                }
            }
        });
    }

    protected void setNameText(String nameText) {
        this.mNameText.setText(nameText);
    }

    protected void setIcon(Drawable icon) {
        this.mIcon.setImageDrawable(icon);
    }

    protected RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    protected CheckBox getReleaseCheckBox() {
        return mReleaseCheckBox;
    }

    protected Future<?> getCurrentTask() {
        return currentTask;
    }

    protected void setReleaseCheckBoxGone() {
        releaseCheckBoxVisible = false;
        mReleaseCheckBox.setVisibility(View.GONE);
    }

    protected void setFailedToLoad(String reasons) {
        String text = activity.getString(R.string.modloader_dl_failed_to_load_list);
        mFailedToLoad.setText(reasons == null ? text : StringUtils.insertNewline(text, reasons));
        AnimUtils.setVisibilityAnim(mFailedToLoad, true);
    }

    protected void cancelFailedToLoad() {
        AnimUtils.setVisibilityAnim(mFailedToLoad, false);
    }

    protected void switchToChild(RecyclerView.Adapter<?> adapter, String title) {
        if (currentTask.isDone() && adapter != null) {
            //保存父级，设置选中的标题文本，切换至子级
            parentAdapter = mRecyclerView.getAdapter();
            mSelectTitle.setText(title);
            hideParentElement(true);
            mRecyclerView.setAdapter(adapter);
            mRecyclerView.scheduleLayoutAnimation();
        }
    }

    @Override
    public YoYo.YoYoString[] slideIn() {
        List<YoYo.YoYoString> yoYos = new ArrayList<>();
        yoYos.add(ViewAnimUtils.setViewAnim(mModsLayout, Techniques.BounceInDown));
        yoYos.add(ViewAnimUtils.setViewAnim(mOperateLayout, Techniques.BounceInLeft));

        yoYos.add(ViewAnimUtils.setViewAnim(mIcon, Techniques.Wobble));
        yoYos.add(ViewAnimUtils.setViewAnim(mNameText, Techniques.FadeInLeft));
        yoYos.add(ViewAnimUtils.setViewAnim(mReturnButton, Techniques.FadeInLeft));
        yoYos.add(ViewAnimUtils.setViewAnim(mRefreshButton, Techniques.FadeInLeft));
        yoYos.add(ViewAnimUtils.setViewAnim(mReleaseCheckBox, Techniques.FadeInLeft));
        YoYo.YoYoString[] array = yoYos.toArray(new YoYo.YoYoString[]{});
        super.setYoYos(array);
        return array;
    }

    @Override
    public YoYo.YoYoString[] slideOut() {
        List<YoYo.YoYoString> yoYos = new ArrayList<>();
        yoYos.add(ViewAnimUtils.setViewAnim(mModsLayout, Techniques.FadeOutUp));
        yoYos.add(ViewAnimUtils.setViewAnim(mOperateLayout, Techniques.FadeOutRight));
        YoYo.YoYoString[] array = yoYos.toArray(new YoYo.YoYoString[]{});
        super.setYoYos(array);
        return array;
    }
}
