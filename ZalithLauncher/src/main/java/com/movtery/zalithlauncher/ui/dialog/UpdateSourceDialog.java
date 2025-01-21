package com.movtery.zalithlauncher.ui.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.databinding.DialogUpdateSourceBinding;
import com.movtery.zalithlauncher.feature.update.LauncherVersion;
import com.movtery.zalithlauncher.feature.update.UpdateLauncher;
import com.movtery.zalithlauncher.task.TaskExecutors;

public class UpdateSourceDialog extends FullScreenDialog implements DraggableDialog.DialogInitializationListener {
    private final DialogUpdateSourceBinding binding = DialogUpdateSourceBinding.inflate(getLayoutInflater());
    private final LauncherVersion launcherVersion;

    public UpdateSourceDialog(@NonNull Context context, LauncherVersion launcherVersion) {
        super(context);
        this.launcherVersion = launcherVersion;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setCancelable(true);
        this.setContentView(binding.getRoot());

        init();
        DraggableDialog.initDialog(this);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void init() {
        binding.githubRelease.setOnClickListener(view -> {
            TaskExecutors.runInUIThread(() -> Toast.makeText(getContext(), getContext().getString(R.string.update_downloading_tip, "Github Release"), Toast.LENGTH_SHORT).show());
            UpdateLauncher updateLauncher = new UpdateLauncher(getContext(), launcherVersion, UpdateLauncher.UpdateSource.GITHUB_RELEASE);
            updateLauncher.start();
            UpdateSourceDialog.this.dismiss();
        });
        binding.ghproxy.setOnClickListener(view -> {
            TaskExecutors.runInUIThread(() -> Toast.makeText(getContext(), getContext().getString(R.string.update_downloading_tip, getContext().getString(R.string.update_update_source_ghproxy)), Toast.LENGTH_SHORT).show());
            UpdateLauncher updateLauncher = new UpdateLauncher(getContext(), launcherVersion, UpdateLauncher.UpdateSource.GHPROXY);
            updateLauncher.start();
            UpdateSourceDialog.this.dismiss();
        });
    }

    @Override
    public Window onInit() {
        return getWindow();
    }
}
