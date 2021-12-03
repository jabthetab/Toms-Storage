package com.tom.storagemod.tile;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import com.tom.storagemod.StorageMod;
import com.tom.storagemod.StoredItemStack;
import com.tom.storagemod.TickerUtil.TickableServer;
import com.tom.storagemod.block.StorageTerminalBase;
import com.tom.storagemod.block.StorageTerminalBase.TerminalPos;
import com.tom.storagemod.gui.ContainerStorageTerminal;
import com.tom.storagemod.item.WirelessTerminal;

public class TileEntityStorageTerminal extends BlockEntity implements MenuProvider, TickableServer {
	private IItemHandler itemHandler;
	private Map<StoredItemStack, Long> items = new HashMap<>();
	private int sort;
	private String lastSearch = "";
	private boolean updateItems;
	public TileEntityStorageTerminal(BlockPos pos, BlockState state) {
		super(StorageMod.terminalTile, pos, state);
	}

	public TileEntityStorageTerminal(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
		super(tileEntityTypeIn, pos, state);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory plInv, Player arg2) {
		return new ContainerStorageTerminal(id, plInv, this);
	}

	@Override
	public Component getDisplayName() {
		return new TranslatableComponent("ts.storage_terminal");
	}

	public Map<StoredItemStack, Long> getStacks() {
		updateItems = true;
		return items;
	}

	public StoredItemStack pullStack(StoredItemStack stack, long max) {
		if(stack != null && itemHandler != null && max > 0) {
			ItemStack st = stack.getStack();
			StoredItemStack ret = null;
			for (int i = 0; i < itemHandler.getSlots(); i++) {
				ItemStack s = itemHandler.getStackInSlot(i);
				if(ItemStack.isSame(s, st) && ItemStack.tagMatches(s, st)) {
					ItemStack pulled = itemHandler.extractItem(i, (int) max, false);
					if(!pulled.isEmpty()) {
						if(ret == null)ret = new StoredItemStack(pulled);
						else ret.grow(pulled.getCount());
						max -= pulled.getCount();
						if(max < 1)break;
					}
				}
			}
			return ret;
		}
		return null;
	}

	public StoredItemStack pushStack(StoredItemStack stack) {
		if(stack != null && itemHandler != null) {
			ItemStack is = ItemHandlerHelper.insertItemStacked(itemHandler, stack.getActualStack(), false);
			if(is.isEmpty())return null;
			else {
				return new StoredItemStack(is);
			}
		}
		return stack;
	}

	public ItemStack pushStack(ItemStack itemstack) {
		StoredItemStack is = pushStack(new StoredItemStack(itemstack));
		return is == null ? ItemStack.EMPTY : is.getActualStack();
	}

	public void pushOrDrop(ItemStack st) {
		if(st.isEmpty())return;
		StoredItemStack st0 = pushStack(new StoredItemStack(st));
		if(st0 != null) {
			Containers.dropItemStack(level, worldPosition.getX() + .5f, worldPosition.getY() + .5f, worldPosition.getZ() + .5f, st0.getActualStack());
		}
	}

	@Override
	public void updateServer() {
		if(updateItems) {
			BlockState st = level.getBlockState(worldPosition);
			Direction d = st.getValue(StorageTerminalBase.FACING);
			TerminalPos p = st.getValue(StorageTerminalBase.TERMINAL_POS);
			if(p == TerminalPos.UP)d = Direction.UP;
			if(p == TerminalPos.DOWN)d = Direction.DOWN;
			BlockEntity invTile = level.getBlockEntity(worldPosition.relative(d));
			items.clear();
			if(invTile != null) {
				LazyOptional<IItemHandler> lih = invTile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, d.getOpposite());
				itemHandler = lih.orElse(null);
				if(itemHandler != null) {
					IntStream.range(0, itemHandler.getSlots()).mapToObj(itemHandler::getStackInSlot).filter(s -> !s.isEmpty()).
					map(StoredItemStack::new).forEach(s -> items.merge(s, s.getQuantity(), (a, b) -> a + b));
				}
			}
			updateItems = false;
		}
	}

	public boolean canInteractWith(Player player) {
		if(level.getBlockEntity(worldPosition) != this)return false;
		int d = 4;
		if(player.getMainHandItem().getItem() instanceof WirelessTerminal)d = Math.max(d, ((WirelessTerminal)player.getMainHandItem().getItem()).getRange(player, player.getMainHandItem()));
		if(player.getOffhandItem().getItem() instanceof WirelessTerminal)d = Math.max(d, ((WirelessTerminal)player.getOffhandItem().getItem()).getRange(player, player.getOffhandItem()));
		return !(player.distanceToSqr(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) > d*2*d*2);
	}

	public int getSorting() {
		return sort;
	}

	public void setSorting(int newC) {
		sort = newC;
	}

	@Override
	public void saveAdditional(CompoundTag compound) {
		compound.putInt("sort", sort);
	}

	@Override
	public void load(CompoundTag compound) {
		sort = compound.getInt("sort");
		super.load(compound);
	}

	public String getLastSearch() {
		return lastSearch;
	}

	public void setLastSearch(String string) {
		lastSearch = string;
	}
}
