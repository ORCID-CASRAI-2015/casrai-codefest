(function() {
    function Storage(db, col) {
		this.MongoClient = require('mongodb').MongoClient
		this.MongoUrl = 'mongodb://localhost:27017/' + (db || "casrai-repeater");
		this.CollectionName = col || 'data';

		this._exec(function(col, done) {
			col.createIndex({"_type": 1});
			col.createIndex({"Funds Request.Reference ID": 1});

			col.findOne({"_type": "ResumptionTokens"}, function(err, doc) {
				if(!doc) {
					col.insertOne({"_type": "ResumptionTokens", "tokens": []}
						, {w:1}
						, function(res, err) {
							done();
						});
				}
			});
		});
    }

    Storage.prototype._exec = function(callback) {
		var CollectionName = this.CollectionName;
		this.MongoClient.connect(this.MongoUrl, function(err, db) {
  			var col = db.collection(CollectionName);
  			callback(col, function() { db.close(); });
  		});
    }

    Storage.prototype.getStatus = function(callback) {
    	this._exec(function(col, done) {
			col
				.find({"_type": "award"})
				.sort({"_id": -1})
				.limit(5)
				.toArray(function(err, lastEntries) {
					col.count({_type:"award"}, function(err, count) {
						var response = {};
						response.count = count;
						response.lastEntries = lastEntries;
						callback(response);
						done();
					});
			});
    	});
    }

    Storage.prototype.getAwards = function(lastObjectId, num, callback) {
    	this._exec(function(col, done) {
			var finder = col.find({"_type": "award"});
			if(lastObjectId) {
				var mongodb = require('mongodb');
				var objectId = new mongodb.ObjectId(lastObjectId);
				finder = finder.filter({"_id": {"$gt": objectId}});
			}
			finder.sort({"_id": 1});
			finder.limit(num);
			finder.toArray(function(err, awards) {
				callback(awards);
				done();
			});
		});
    }

    Storage.prototype.saveAwards = function(awards) {
    	var commands = [];
    	for(var i=0; i < awards.length; i++) {
    		var award = awards[i];
    		award._type = 'award';
    		commands.push({ deleteOne: { filter: {"Funds Request":{"Reference ID":award["Funds Request"]["Reference ID"]}}}});
    		commands.push({ insertOne: { document: award } });
    	}
		this._exec(function(col, done) {
  			col.bulkWrite(commands, {ordered:true, w:1}, function(err, r) {
				done();
			});
		});
    }

    Storage.prototype.getResumptionToken = function(key, callback) {
		this._exec(function(col, done) {
	    	col.findOne({"_type": "ResumptionTokens"}, function(err, doc) {
	    		try {
		    		var tokens = doc['tokens'];
		    		if(!tokens) {
		    			tokens = [];
		    			doc['tokens'] = tokens;
		    		}
		    		for(var i=0; i<tokens.length; i++) {
		    			if(key == tokens[i]['url']) {
		    				callback(tokens[i]['token']);
		    				return;
		    			}
		    		}
		    		callback(null);
		    	} finally {
		    		done();
		    	}
	    	});
    	});
    }

    Storage.prototype.setResumptionToken = function(key, token) {
		this._exec(function(col, done) {
	    	col.findOne({"_type": "ResumptionTokens"}, function(err, doc) {
	    		var tokens = doc['tokens'];
	    		if(!tokens) {
	    			tokens = [];
	    			doc['tokens'] = tokens;
	    		}
	    		var found = false;
	    		for(var i=0; i<tokens.length; i++) {
	    			if(key == tokens[i]['url']) {
	    				found = true;
	    				tokens[i]['token'] = token;
	    				break;
	    			}
	    		}
	    		if(!found) {
	    			tokens.push({"url":key, "token":token});
	    		}
	    		col.findAndModify(
	    			{ "_type": "ResumptionTokens"}
	    			, null
	    			, {"$set" : {"tokens":tokens}}
	    			, {j:true}
	    			, function(err, doc) {
	    				done();
	    			}
				);
			});
		});
	}


    module.exports.Storage = Storage;

}());
