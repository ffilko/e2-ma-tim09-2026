package com.example.slagalica;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.slagalica.data.LeaderboardManager;

public class RewardDialog {

    public static void show(Context context, LeaderboardManager.RewardPopup popup, Runnable onDismiss) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_reward);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#B3000000")));
            window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }

        ImageView trophy = dialog.findViewById(R.id.ivTrophy);
        TextView title = dialog.findViewById(R.id.tvRewardTitle);
        TextView message = dialog.findViewById(R.id.tvRewardMessage);
        TextView tokens = dialog.findViewById(R.id.tvRewardTokens);
        Button ok = dialog.findViewById(R.id.btnRewardOk);
        final ConfettiView confetti = dialog.findViewById(R.id.confetti);

        String cycleName = LeaderboardManager.TYPE_MONTHLY.equals(popup.type)
                ? "mesečnoj" : "nedeljnoj";
        title.setText(popup.rank + ". mesto!");
        String msg = popup.message != null ? popup.message
                : ("Osvojio/la si " + popup.rank + ". mesto na " + cycleName + " rang listi!");
        message.setText(msg);
        tokens.setText("+" + popup.tokens + " 🪙");

        ScaleAnimation pop = new ScaleAnimation(
                0f, 1f, 0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        pop.setDuration(650);
        pop.setInterpolator(new OvershootInterpolator(2.5f));
        trophy.startAnimation(pop);

        final MediaPlayer[] player = { null };
        try {
            player[0] = MediaPlayer.create(context, R.raw.reward);
            if (player[0] != null) player[0].start();
        } catch (Exception ignored) {}

        ok.setOnClickListener(v -> {
            if (confetti != null) confetti.stop();
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> {
            if (player[0] != null) {
                try { player[0].release(); } catch (Exception ignored) {}
                player[0] = null;
            }
            if (onDismiss != null) onDismiss.run();
        });

        dialog.show();
    }
}
