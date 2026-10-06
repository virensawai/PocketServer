package com.example.pocketserver.ui.qr;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.pocketserver.R;
import com.example.pocketserver.databinding.BottomSheetQrBinding;
import com.example.pocketserver.ui.util.QrCodeGenerator;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Modal bottom sheet presenting a high-resolution QR code for the active deployment,
 * with one-tap copy, share, and browser launch actions.
 */
public class QrCodeBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_URL = "arg_url";

    public static QrCodeBottomSheetDialogFragment newInstance(@NonNull String url) {
        QrCodeBottomSheetDialogFragment fragment = new QrCodeBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    private BottomSheetQrBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String url = getArguments() != null ? getArguments().getString(ARG_URL, "") : "";
        binding.txtQrUrl.setText(url);

        if (!url.isEmpty()) {
            try {
                Bitmap qrBitmap = QrCodeGenerator.generate(url, 512);
                binding.imgQrCode.setImageBitmap(qrBitmap);
            } catch (Exception e) {
                binding.imgQrCode.setImageResource(R.drawable.ic_error);
                Toast.makeText(requireContext(), "Failed to generate QR code", Toast.LENGTH_SHORT).show();
            }
        }

        binding.btnQrCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("PocketServer URL", url));
                Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnQrShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, url);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
        });

        binding.btnQrOpen.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No browser available to open URL", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
