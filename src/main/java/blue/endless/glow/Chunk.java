package blue.endless.glow;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import com.playsawdust.chipper.glow.RenderScheduler;
import com.playsawdust.chipper.glow.gl.BakedModel;
import com.playsawdust.chipper.glow.gl.shader.Destroyable;
import com.playsawdust.chipper.glow.mesher.VoxelMesher;
import com.playsawdust.chipper.glow.model.Model;
import com.playsawdust.chipper.glow.scene.Actor;
import com.playsawdust.chipper.glow.scene.CollisionVolume;
import com.playsawdust.chipper.glow.voxel.MeshableVoxel;
import com.playsawdust.chipper.glow.voxel.VoxelPatch;

public class Chunk implements Destroyable, Actor {
	private Vector3d position = new Vector3d();;
	private VoxelPatch patch;
	private Model model;
	private BakedModel bakedModel;
	private boolean modelDirty = true;
	private boolean bakeDirty = true;
	
	private Chunk() {}
	
	public boolean isModelDirty() {
		return modelDirty;
	}
	
	public boolean isBakeDirty() {
		return bakeDirty;
	}
	
	public boolean isDirty() {
		return modelDirty || bakeDirty;
	}
	
	public void mesh() {
		model = VoxelMesher.mesh(0, 0, 0, 32, 32, 32, patch::getShape, patch::getMaterial);
		modelDirty = false;
	}
	
	public void bake(RenderScheduler scheduler) {
		if (model==null) mesh();
		if (bakedModel!=null) bakedModel.destroy();
		bakedModel = scheduler.bake(model);
		bakeDirty = false;
	}
	
	public Model getModel() {
		return model;
	}
	
	/** Gets block from chunk-local coords */
	public Block getBlock(int x, int y, int z) {
		if (patch==null) return Block.NOTHING;
		MeshableVoxel voxel = patch.getVoxel(x, y, z);
		if (voxel instanceof Block) {
			return (Block)voxel;
		} else {
			//System.out.println("PALETTE VIOLATION");
			return (Block) Block.NOTHING; //the palette is poisoned!
		}
	}
	
	public void setBlock(int x, int y, int z, Block block) {
		patch.setVoxel(x, y, z, block, true);
		modelDirty = true;
		bakeDirty = true;
	}
	
	public int getX() {
		return (int)position.x;
	}
	
	public int getY() {
		return (int)position.y;
	}
	
	public int getZ() {
		return (int)position.z;
	}
	
	
	@Override
	public void destroy() {
		if (bakedModel!=null) {
			bakedModel.destroy();
			bakedModel = null;
			bakeDirty = true;
		}
	}

	@Override
	public Vector3d getPosition(Vector3d result) {
		if (result==null) result = new Vector3d();
		result.set(position);
		return result;
	}

	@Override
	public Matrix3d getOrientation(Matrix3d result) {
		if (result==null) result = new Matrix3d();
		result.identity(); //We could remove this no-rotate restriction if we wanted to enable airships
		return result;
	}

	@Override
	public @Nullable BakedModel getRenderObject() {
		return bakedModel;
	}

	@Override
	public @Nullable CollisionVolume getCollision() {
		return null; //This will change!
	}

	@Override
	public void setPosition(Vector3dc position) {
		this.position.set(position);
	}

	@Override
	public void setOrientation(Matrix3dc orientation) {
		//no.
	}
	
	
	public static Chunk create() {
		Chunk result = new Chunk();
		result.patch = new VoxelPatch();
		result.patch.setSize(32, 32, 32);
		return result;
	}
}
