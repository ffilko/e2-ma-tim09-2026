package com.example.slagalica;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.slagalica.ui.fragments.games.KorakPoKorakFragment;
import com.example.slagalica.ui.fragments.games.MojBrojFragment;


public class GameFragment extends Fragment {

    private final Class<? extends Fragment>[] games = new Class[]{
            KorakPoKorakFragment.class,
            MojBrojFragment.class
    };

    private int currentGameIndex = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, container, false);
    }

    @Override
    public void onViewCreated( View view,  Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getChildFragmentManager().setFragmentResultListener("game_finished", getViewLifecycleOwner(), (requestKey, bundle) -> {
            loadNextGame();
        });

        if (savedInstanceState == null) {
            startFirstGame();
        }
    }

    private void startFirstGame() {
        try {
            Fragment firstGame = games[0].newInstance();
            loadChildFragment(firstGame, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadNextGame() {
        currentGameIndex++;
        if (currentGameIndex < games.length) {
            try {
                Fragment nextGame = games[currentGameIndex].newInstance();
                loadChildFragment(nextGame, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigate(new GameMenuFragment(), false);
            }
        }
    }

    private void loadChildFragment(Fragment fragment, boolean animate) {
        FragmentTransaction tx = getChildFragmentManager().beginTransaction();
        if (animate) {
            tx.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        }
        tx.replace(R.id.gameFragmentContainer, fragment).commit();
    }
}