/*
 * Copyright 2015 Alfresco Software, Ltd.  All rights reserved.
 *
 * License rights for this program may be obtained from Alfresco Software, Ltd. 
 * pursuant to a written agreement and any use of this program without such an 
 * agreement is prohibited. 
 */
package org.alfresco.cacheserver.dao.mongo;

import org.alfresco.cacheserver.CacheServerIdentity;
import org.alfresco.cacheserver.dao.ContentDAO;
import org.alfresco.cacheserver.entity.NodeInfo;
import org.alfresco.cacheserver.entity.NodeUsage;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

/**
 * 
 * @author sglover
 *
 */
public class MongoContentDAO implements ContentDAO
{
	private DB db;

	private String contentCollectionName;
	private DBCollection contentData;
	private String contentUsageCollectionName;
	private DBCollection contentUsageData;
	private CacheServerIdentity cacheServerIdentity;

	public MongoContentDAO(DB db, String contentCollectionName, String contentUsageCollectionName,
			CacheServerIdentity cacheServerIdentity) throws Exception
	{
		this.db = db;
		this.contentCollectionName = contentCollectionName;
		this.contentUsageCollectionName = contentUsageCollectionName;
		this.cacheServerIdentity = cacheServerIdentity;
		init();
	}

	public void drop()
	{
		contentData.drop();
	}

	protected DBCollection getCollection(DB db, String collectionName, WriteConcern writeConcern)
	{
	    if(!db.collectionExists(collectionName))
	    {
	        DBObject options = new BasicDBObject();
	        db.createCollection(collectionName, options);
	    }
	    DBCollection collection = db.getCollection(collectionName);
	    collection.setWriteConcern(writeConcern);

	    return collection;
	}

    protected DBCollection getCappedCollection(DB db, String collectionName, Integer maxCollectionSize, Integer maxDocuments, WriteConcern writeConcern)
    {
        if(!db.collectionExists(collectionName))
        {
            BasicDBObjectBuilder builder = BasicDBObjectBuilder
                    .start();

            builder.add("capped", true);
            
            if(maxCollectionSize != null)
            {
                builder.add("size", maxCollectionSize);
            }

            if(maxDocuments != null)
            {
                builder.add("max", maxDocuments);
            }

            DBObject options = builder.get();
            db.createCollection(collectionName, options);
        }
        DBCollection collection = db.getCollection(collectionName);
        collection.setWriteConcern(writeConcern);

        return collection;
    }

	protected void checkResult(WriteResult result, int expectedNum)
	{
	    boolean ok = result.getLastError().ok();
	    if(!ok)
	    {
	        throw new RuntimeException("Mongo write failed");
	    }
	    if(expectedNum != result.getN())
	    {
	        throw new RuntimeException("Mongo write failed, expected " + expectedNum + " writes, got " + result.getN());
	    }
	}

	
	protected void checkResult(WriteResult result)
	{
        boolean ok = result.getLastError().ok();
        if(!ok)
        {
            throw new RuntimeException("Mongo write failed");
        }
	}

	public void init()
	{
        if (db == null)
        {
            throw new RuntimeException("Mongo DB must not be null");
        }

		this.contentData = getCollection(db, contentCollectionName, WriteConcern.ACKNOWLEDGED);
		this.contentUsageData = getCollection(db, contentUsageCollectionName, WriteConcern.ACKNOWLEDGED);

		{
	        DBObject keys = BasicDBObjectBuilder
	        		.start("e", 1)
	                .add("n", 1)
	                .add("v", 1)
	                .get();
	        this.contentData.ensureIndex(keys, "byNodeId", false);
		}

		{
	        DBObject keys = BasicDBObjectBuilder
	        		.start("e", 1)
	                .add("n", 1)
	                .add("v", 1)
	                .get();
	        this.contentData.ensureIndex(keys, "byNodePath", false);
		}

		{
	        DBObject keys = BasicDBObjectBuilder
	        		.start("e", 1)
	        		.add("n", 1)
	                .add("v", 1)
	                .get();
	        this.contentUsageData.ensureIndex(keys, "main", false);
		}
	}

	private NodeInfo toNodeInfo(DBObject dbObject)
	{
		NodeInfo nodeInfo = null;

		if(dbObject != null)
		{
			String nodePath = (String)dbObject.get("p");
			String contentPath = (String)dbObject.get("c");
			String nodeId = (String)dbObject.get("n");
			String nodeVersion = (String)dbObject.get("v");
			String mimeType = (String)dbObject.get("m");
			Long size = (Long)dbObject.get("s");
	
			nodeInfo = new NodeInfo(nodeId, nodeVersion, nodePath, contentPath, mimeType, size);
		}

		return nodeInfo;
	}
	
	private DBObject fromNodeInfo(NodeInfo nodeInfo)
	{
		BasicDBObjectBuilder builder = BasicDBObjectBuilder
				.start("e", cacheServerIdentity.getId())
				.add("p", nodeInfo.getNodePath())
				.add("c", nodeInfo.getContentPath())
				.add("n", nodeInfo.getNodeId())
				.add("v", nodeInfo.getNodeVersion())
				.add("m", nodeInfo.getMimeType())
				.add("s", nodeInfo.getSize());
		return builder.get();
	}

	private NodeUsage toNodeUsage(DBObject dbObject)
	{
		NodeUsage nodeUsage = null;

		if(dbObject != null)
		{
			String nodeId = (String)dbObject.get("n");
			String nodeVersion = (String)dbObject.get("v");
			Long timestamp = (Long)dbObject.get("t");
			String username = (String)dbObject.get("u");
	
			nodeUsage = new NodeUsage(nodeId, nodeVersion, timestamp, username);
		}

		return nodeUsage;
	}
	
	private DBObject fromNodeUsageInfo(NodeUsage nodeUsage)
	{
		BasicDBObjectBuilder builder = BasicDBObjectBuilder
				.start("e", cacheServerIdentity.getId())
				.add("n", nodeUsage.getNodeId())
				.add("v", nodeUsage.getNodeVersion())
				.add("t", nodeUsage.getTimestamp())
				.add("u", nodeUsage.getUsername());
		return builder.get();
	}

	@Override
	public NodeInfo getByNodePath(String nodePath)
	{
		QueryBuilder queryBuilder = QueryBuilder
				.start("e").is(cacheServerIdentity.getId())
				.and("p").is(nodePath);
		DBObject query = queryBuilder.get();

		DBObject dbObject = contentData.findOne(query);
		NodeInfo nodeInfo = toNodeInfo(dbObject);
		return nodeInfo;
	}

	@Override
	public NodeInfo getByNodeId(String nodeId, String nodeVersion)
	{
		QueryBuilder queryBuilder = QueryBuilder
				.start("e").is(cacheServerIdentity.getId())
				.and("n").is(nodeId)
				.and("v").is(nodeVersion);
		DBObject query = queryBuilder.get();

		DBObject dbObject = contentData.findOne(query);
		NodeInfo nodeInfo = toNodeInfo(dbObject);
		return nodeInfo;
	}

	@Override
	public void updateNode(NodeInfo nodeInfo)
	{
		String cacheServerId = cacheServerIdentity.getId();
		String nodeId = nodeInfo.getNodeId();
		String nodeVersion = nodeInfo.getNodeVersion();
		String mimeType = nodeInfo.getMimeType();
		Long size = nodeInfo.getSize();
		String nodePath = nodeInfo.getNodePath();
		String contentPath = nodeInfo.getContentPath();

		QueryBuilder queryBuilder = QueryBuilder
				.start("e").is(cacheServerId)
				.and("n").is(nodeId)
				.and("v").is(nodeVersion);
		DBObject query = queryBuilder.get();

		BasicDBObjectBuilder builder = BasicDBObjectBuilder
				.start("$set", BasicDBObjectBuilder
						.start("m", mimeType)
						.add("s", size)
						.add("c", contentPath)
						.add("p", nodePath)
						.get());
		DBObject update = builder.get();

		WriteResult result = contentData.update(query, update, true, false);
		checkResult(result);
	}

	@Override
	public void addUsage(NodeUsage nodeUsage)
	{
		DBObject insert = fromNodeUsageInfo(nodeUsage);
		WriteResult result = contentUsageData.insert(insert);
		checkResult(result);
	}
}
