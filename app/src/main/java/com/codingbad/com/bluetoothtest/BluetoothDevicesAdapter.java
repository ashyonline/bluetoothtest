package com.codingbad.com.bluetoothtest;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */
public class BluetoothDevicesAdapter extends RecyclerView.Adapter<BluetoothDevicesAdapter.ViewHolder> {

    private List<BluetoothDeviceWithStrength> bluetoothDevices;
    private RecyclerViewListener recyclerViewListener;

    public BluetoothDevicesAdapter(RecyclerViewListener recyclerViewListener) {
        this.recyclerViewListener = recyclerViewListener;
        this.bluetoothDevices = new ArrayList<>();
    }

    @Override
    public int getItemViewType(int position) {
        return 1;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        BluetoothDeviceView view = new BluetoothDeviceView(parent.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final BluetoothDeviceWithStrength bluetoothDevice = bluetoothDevices.get(position);
        holder.bluetoothDeviceView.fill(bluetoothDevice);
    }

    public void addItem(BluetoothDeviceWithStrength bluetoothDevice) {
        this.bluetoothDevices.add(bluetoothDevice);
        notifyItemInserted(getItemCount());
    }

    public void addItemList(List<BluetoothDeviceWithStrength> bluetoothDevices) {
        this.bluetoothDevices.addAll(bluetoothDevices);
        notifyDataSetChanged();
    }

    public void removeAll() {
        this.bluetoothDevices.clear();
        notifyDataSetChanged();
    }

    public void removeItemAt(int position) {
        this.bluetoothDevices.remove(position);
        notifyItemRemoved(position);
    }

    public BluetoothDeviceWithStrength removeItem(int position) {
        final BluetoothDeviceWithStrength removed = this.bluetoothDevices.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    public BluetoothDeviceWithStrength getItemAtPosition(int position) {
        return this.bluetoothDevices.get(position);
    }

    @Override
    public int getItemCount() {
        return this.bluetoothDevices.size();
    }

    public void animateTo(List<BluetoothDeviceWithStrength> models) {
        applyAndAnimateRemovals(models);
        applyAndAnimateAdditions(models);
        applyAndAnimateMovedItems(models);
    }

    private void applyAndAnimateRemovals(List<BluetoothDeviceWithStrength> newItems) {
        for (int i = bluetoothDevices.size() - 1; i >= 0; i--) {
            final BluetoothDeviceWithStrength bluetoothDevice = bluetoothDevices.get(i);
            if (!newItems.contains(bluetoothDevice)) {
                removeItem(i);
            }
        }
    }

    private void applyAndAnimateAdditions(List<BluetoothDeviceWithStrength> newItems) {
        for (int i = 0, count = newItems.size(); i < count; i++) {
            final BluetoothDeviceWithStrength bluetoothDevice = newItems.get(i);
            if (!bluetoothDevices.contains(bluetoothDevice)) {
                addItem(i, bluetoothDevice);
            }
        }
    }

    public void addItem(int position, BluetoothDeviceWithStrength bluetoothDevice) {
        this.bluetoothDevices.add(position, bluetoothDevice);
        notifyItemInserted(getItemCount());
    }


    private void applyAndAnimateMovedItems(List<BluetoothDeviceWithStrength> newItems) {
        for (int toPosition = newItems.size() - 1; toPosition >= 0; toPosition--) {
            final BluetoothDeviceWithStrength bluetoothDevice = newItems.get(toPosition);
            final int fromPosition = bluetoothDevices.indexOf(bluetoothDevice);
            if (fromPosition >= 0 && fromPosition != toPosition) {
                moveItem(fromPosition, toPosition);
            }
        }
    }

    public void moveItem(int fromPosition, int toPosition) {
        final BluetoothDeviceWithStrength moved = this.bluetoothDevices.remove(fromPosition);
        this.bluetoothDevices.add(toPosition, moved);
        notifyItemMoved(fromPosition, toPosition);
    }

    public interface RecyclerViewListener {
        void onItemClickListener(View view, int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final BluetoothDeviceView bluetoothDeviceView;

        public ViewHolder(BluetoothDeviceView itemView) {
            super(itemView);
            this.bluetoothDeviceView = itemView;
            this.bluetoothDeviceView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            recyclerViewListener.onItemClickListener(v, getAdapterPosition());
        }
    }
}


