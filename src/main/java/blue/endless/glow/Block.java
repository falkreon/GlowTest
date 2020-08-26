package blue.endless.glow;

import com.playsawdust.chipper.glow.model.Material;
import com.playsawdust.chipper.glow.voxel.MeshableVoxel;
import com.playsawdust.chipper.glow.voxel.VoxelShape;

public class Block implements MeshableVoxel {
	public static Block NOTHING = new Block().setShape(VoxelShape.EMPTY);
	
	protected VoxelShape shape = VoxelShape.CUBE;
	protected Material material =  Material.BLANK;
	

	@Override
	public Material getMaterial() {
		return material;
	}

	@Override
	public VoxelShape getShape() {
		return shape;
	}
	
	public Block setShape(VoxelShape shape) {
		this.shape = shape;
		return this;
	}
	
	public Block setMaterial(Material material) {
		this.material = material;
		return this;
	}
}
