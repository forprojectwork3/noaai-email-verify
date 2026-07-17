package com.example.ailecturesummarizer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ailecturesummarizer.R;
import com.example.ailecturesummarizer.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the chatbot screen's RecyclerView.
 * Shows user messages right-aligned (gradient bubble) and bot messages left-aligned (card bubble).
 * Uses the item_chat_message.xml layout which contains both containers; one is toggled visible per row.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    /** Remove the last message from the list (e.g., a typing-indicator placeholder). */
    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            int last = messages.size() - 1;
            messages.remove(last);
            notifyItemRemoved(last);
        }
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout containerUser;
        private final LinearLayout containerBot;
        private final TextView tvUserMessage;
        private final TextView tvBotMessage;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            containerUser = itemView.findViewById(R.id.containerUser);
            containerBot  = itemView.findViewById(R.id.containerBot);
            tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
            tvBotMessage  = itemView.findViewById(R.id.tvBotMessage);
        }

        void bind(ChatMessage msg) {
            if (msg.isUser()) {
                containerUser.setVisibility(View.VISIBLE);
                containerBot.setVisibility(View.GONE);
                tvUserMessage.setText(msg.getMessage());
            } else {
                containerUser.setVisibility(View.GONE);
                containerBot.setVisibility(View.VISIBLE);
                tvBotMessage.setText(msg.getMessage());
            }
        }
    }
}
