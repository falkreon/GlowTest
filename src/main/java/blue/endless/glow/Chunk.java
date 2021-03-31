package blue.endless.glow;

import java.util.ArrayList;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import com.playsawdust.chipper.glow.RenderScheduler;
import com.playsawdust.chipper.glow.gl.BakedModel;
import com.playsawdust.chipper.glow.gl.OffheapResource;
import com.playsawdust.chipper.glow.mesher.VoxelMesher;
import com.playsawdust.chipper.glow.model.Model;
import com.playsawdust.chipper.glow.scene.Actor;
import com.playsawdust.chipper.glow.scene.Camera;
import com.playsawdust.chipper.glow.scene.CollisionVolume;
import com.playsawdust.chipper.glow.util.AbstractGPUResource;
import com.playsawdust.chipper.glow.voxel.MeshableVoxel;
import com.playsawdust.chipper.glow.voxel.VoxelPatch;

public class Chunk extends AbstractGPUResource implements Actor {
	private Vector3d position = new Vector3d();;
	private VoxelPatch patch;
	private ArrayList<Model> modelLods = new ArrayList<>();
	private ArrayList<BakedModel> bakedLods = new ArrayList<>();
	private boolean modelDirty = true;
	private boolean bakeDirty = true;
	private boolean empty = false;
	
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
		modelLods.clear();
		if (isEmpty()) {
			modelDirty = false;
			empty = true;
			return;
		} else {
			empty = false;
		}
		
		boolean allLossless = true;
		
		Model model = VoxelMesher.mesh(0, 0, 0, 32, 32, 32, patch::getShape, patch::getMaterial, 1);
		modelLods.add(model);
		
		VoxelPatch lod = patch.getLoD();
		allLossless &= lod.isLossless();
		Model lodModel = VoxelMesher.mesh(0,0,0,16,16,16, lod::getShape, lod::getMaterial, 2);
		if (lodModel.isEmpty()) {
			modelDirty = false;
			return;
		} else {
			if (allLossless) modelLods.clear();
			modelLods.add(lodModel);
		}
		
		lod = lod.getLoD();
		allLossless &= lod.isLossless();
		lodModel = VoxelMesher.mesh(0,0,0,8,8,8, lod::getShape, lod::getMaterial, 4);
		if (lodModel.isEmpty()) {
			modelDirty = false;
			return;
		} else {
			if (allLossless) modelLods.clear();
			modelLods.add(lodModel);
		}
		
		lod = lod.getLoD();
		allLossless &= lod.isLossless();
		lodModel = VoxelMesher.mesh(0,0,0,4,4,4, lod::getShape, lod::getMaterial, 8);
		if (lodModel.isEmpty()) {
			modelDirty = false;
			return;
		} else {
			if (allLossless) modelLods.clear();
			modelLods.add(lodModel);
		}
		
		lod = lod.getLoD();
		allLossless &= lod.isLossless();
		lodModel = VoxelMesher.mesh(0,0,0,2,2,2, lod::getShape, lod::getMaterial, 16);
		if (lodModel.isEmpty()) {
			modelDirty = false;
			return;
		} else {
			if (allLossless) modelLods.clear();
			modelLods.add(lodModel);
		}
		
		modelDirty = false;
	}
	
	public void bake(RenderScheduler scheduler) {
		if (modelDirty) mesh();
		
		if (empty) {
			bakeDirty = false;
			return;
		}
		
		for(BakedModel m : bakedLods) m.free();
		bakedLods.clear();
		for(Model m : modelLods) {
			bakedLods.add(scheduler.bake(m));
		}
		modelLods.clear();
		modelDirty = true;
		bakeDirty = false;
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
	public void _free() {
		for(BakedModel m : bakedLods) m.free();
		bakedLods.clear();
		bakeDirty = true;
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
	public @Nullable BakedModel getRenderObject(Camera camera) {
		if (bakedLods.size()==0) return null;
		
		double distance = camera.getPosition(null).distance(position);
		
		int lod = (int)(Math.log10(distance/64.0)*2.0);
		//int lod = (int)(camera.getPosition(null).distance(position)/110);
		if (lod>=bakedLods.size()) lod = bakedLods.size()-1;
		if (lod<0) lod=0;
		//int lod = (int)(chunk.getPosition(null).distanceSquared(0, 0, 0)/150_000);
		
		return bakedLods.get(lod);
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
	
	public boolean isEmpty() {
		int[] patchData = patch.getData();
		for(int i=0; i<patchData.length; i++) {
			if (patchData[i]!=0) return false;
		}
		
		return true;
	}
	
	public static Chunk create() {
		Chunk result = new Chunk();
		result.patch = new VoxelPatch();
		result.patch.setSize(32, 32, 32);
		return result;
	}
}
