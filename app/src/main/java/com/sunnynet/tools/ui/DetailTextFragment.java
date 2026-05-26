package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.sunnynet.tools.R;

/**
 * 详情 Tab 页：展示单段只读文本。
 */
public class DetailTextFragment extends Fragment {

    private static final String ARG_TEXT = "text";

    public static DetailTextFragment newInstance(CharSequence text) {
        DetailTextFragment fragment = new DetailTextFragment();
        Bundle args = new Bundle();
        args.putCharSequence(ARG_TEXT, text);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView textView = view.findViewById(R.id.detail_page_text);
        Bundle args = getArguments();
        CharSequence text = "";
        if (args != null) {
            text = args.getCharSequence(ARG_TEXT, "");
        }
        textView.setText(text);
    }
}
