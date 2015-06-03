(function() {
    function WS(storage, port, filter) {
    	this.storage = storage;
    	this.filter = filter;

		var restify = require('restify');

		var server = restify.createServer();
		server.use(restify.queryParser());

		// Status
		server.get('/status', function(req, res, next) {
	    	var status = storage.getStatus(function(status) {
		    	if(filter)
		    		status.filter = filter;
				res.set('content-type', 'application/json; charset=utf-8');
				res.json(status);
				next();
	    	});
		});

		// Funding
		server.get('/fundingResults', function(req, res, next) {
			var lastObjectId = req.params.resumptionToken;

			var awards = storage.getAwards(lastObjectId, 10, function(awards) {
				var response = {};
				response.awards = awards;
				if (awards.length > 0)
					lastObjectId = awards[awards.length - 1]['_id'];
				for (var i=0; i<awards.length; i++) {
					var award = awards[i];
					delete award['_id'];
					delete award['_type'];
				}
				response.resumptionToken = lastObjectId;

				res.send(response);
				next();
			});
		});

		server.listen(port || 8080, function() {
		  console.log('%s listening at %s', server.name, server.url);
		});	
	}

    module.exports.WS = WS;

}());
