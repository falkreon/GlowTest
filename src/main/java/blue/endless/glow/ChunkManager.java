package blue.endless.glow;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3i;

import com.playsawdust.chipper.glow.RenderScheduler;
import com.playsawdust.chipper.glow.util.AbstractGPUResource;
import com.playsawdust.chipper.glow.voxel.VoxelShape;

public class ChunkManager extends AbstractGPUResource {
	private static final int maxRenderDist = 32; //Just over 2GiB used for chunk management at 2,197,000 bytes
	
	private int renderDist = 8;
	private int mapSize = renderDist * 2 + 1;
	private int xSize = mapSize;
	private int zSize = mapSize;
	private int ySize = mapSize/2;
	
	private Chunk[] chunks = new Chunk[mapSize * mapSize * mapSize];
	
	private int xofs = 0;
	private int yofs = 0;
	private int zofs = 0;
	
	public void resize(int renderDist) {
		this.renderDist = renderDist;
		//mapSize = renderDist * 2 + 1;
		xSize = renderDist*2+1;
		zSize = xSize;
		ySize = xSize / 2;
		Chunk[] newChunks = new Chunk[xSize * zSize * ySize];
		
		//TODO: copy chunks over
		
		chunks = newChunks;
	}
	
	/*
	public void pan(int dx, int dy, int dz) {
		Chunk[] newChunks = new Chunk[mapSize * mapSize * mapSize];
		for(int y=0; y<mapSize; y++) {
			for(int z=0; z<mapSize; z++) {
				for(int x=0; x<mapSize; x++) {
					int destOfs = chunkCoordOfs(x,y,z);
					int srcOfs = chunkCoordOfs(x+dx, y+dy, z+dz);
					if (srcOfs==-1) {
						newChunks[destOfs] = null;
					} else {
						newChunks[destOfs] = chunks[srcOfs];
					}
				}
			}
		}
		
		//TODO: Destroy old chunks
		Arrays.fill(chunks, null); //May be unnecessary but it helps the GC to untangle fewer refs
		chunks = newChunks;
		xofs += dx;
		yofs += dy;
		zofs += dz;
	}*/
	
	private int chunkCoordOfs(int x, int y, int z) {
		x -= xofs;
		y -= yofs;
		z -= zofs;
		
		if (x<0 || x>=xSize || y<0 || y>=ySize || z<0 || z>=zSize) return -1;
		return x + (xSize*z) + (xSize*zSize*y); //xzy, so that you can grab x slivers and arraycopy them around to pan in the x direction, or grab whole xz slabs and arraycopy them to pan in the z direction
	}
	
	public void set(int x, int y, int z, Chunk chunk) {
		int ofs = chunkCoordOfs(x, y, z);
		if (ofs!=-1) chunks[ofs] = chunk;
	}
	
	
	public void scheduleAll(List<Vector3i> list) {
		for(int y=0; y<ySize; y++) {
			for(int z=0; z<zSize; z++) {
				for(int x=0; x<xSize; x++) {
					int ofs = chunkCoordOfs(x+xofs, y+yofs, z+zofs);
					if (ofs!=-1) {
						if (chunks[ofs]==null) {
							list.add(new Vector3i(x+xofs, y+yofs, z+zofs));
						}
					}
				}
			}
		}
		list.sort(getCenterComparator());
	}
	
	//public void getSchedulableChunks<List<Chunk> list) {
		
	//}
	
	public VoxelShape getShape(int x, int y, int z) {
		int chunkX = x / 32;
		int chunkY = y / 32;
		int chunkZ = z / 32;
		int ofs = chunkCoordOfs(chunkX, chunkY, chunkZ);
		if (ofs==-1) return VoxelShape.EMPTY;
		Chunk chunk = chunks[ofs];
		if (chunk==null) return VoxelShape.EMPTY;
		Block block = chunk.getBlock(x%32, y%32, z%32);
		if (block==null) return VoxelShape.EMPTY;
		return block.getShape();
	}
	
	public void setBlock(int x, int y, int z, Block block, RenderScheduler scheduler) {
		int chunkX = x / 32;
		int chunkY = y / 32;
		int chunkZ = z / 32;
		int ofs = chunkCoordOfs(chunkX, chunkY, chunkZ);
		if (ofs==-1) return;
		Chunk chunk = chunks[ofs];
		if (chunk==null) return;
		chunk.setBlock(x%32, y%32, z%32, block);
		chunk.mesh();
		chunk.bake(scheduler);
	}
	
	public Comparator<Vector3i> getCenterComparator() {
		Vector3dc MAP_CENTER = new Vector3d((xofs+renderDist+0.5)*32, (yofs+renderDist+0.5)*32, (zofs+renderDist+0.5)*32);
		return (a, b)->(int)(MAP_CENTER.distanceSquared(new Vector3d(b)) - MAP_CENTER.distanceSquared(new Vector3d(a)));
	}
	
	@Override
	public void _free() {
		for(int i=0; i<chunks.length; i++) {
			if (chunks[i] != null) {
				chunks[i].free();
				chunks[i] = null;
			}
		}
	}
}
