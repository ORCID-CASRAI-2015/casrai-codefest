(function() {
    function Harvester(storage, urls, filter) {
    	this.storage = storage;
    	this.urls = urls;
    	this.filter = filter;
    	this.timeout = 2000;

    	setTimeout(function(harvester) {harvester.harvest();}, this.timeout, this);
	}

	Harvester.prototype.harvest = function() {
		for(var i=0; i<this.urls.length; i++) {
			var url = this.urls[i];
			try {
				this.harvestSource(url);
			} catch(err) {
				console.log("Got error processing %s: %s", url, err);
			}
		}
    	setTimeout(function(harvester) {harvester.harvest();}, this.timeout, this);
	}

	Harvester.prototype.harvestSource = function(url) {
		var processAward = this.processAward;
		var filter = this.filter;
		var storage = this.storage;
		var client = require('restify').createJsonClient({
		  url: url,
		  version: '*'
		});

		storage.getResumptionToken(url, function(resumptionToken) {
			var arguments = {};
			var method = '/fundingResults';
			if(resumptionToken)
				method += "?resumptionToken=" + resumptionToken;
			client.get(method, function(err, req, res, data) {
				var awards = data.awards;
				var actualAwards = [];
				for(var i=0; i<awards.length; i++) {
					var award = awards[i];
					processAward(award);
					if(filter) {
						var amountStr = award['Funding Award']['Amount'].split(' ')[0];
						var amount = parseFloat(amountStr);
						var even = amount % 2 == 0;
						if((even && filter == 'even') || (!even && filter == 'odd'))
							actualAwards.push(award);
					} else {
						actualAwards.push(award);
					}
				}

				if(actualAwards.length > 0) {
					console.log("Got", actualAwards.length, "awards from", url);
					storage.saveAwards(actualAwards);
				}
				storage.setResumptionToken(url, data.resumptionToken);
			});
		});

	}

	Harvester.prototype.processAward = function(award) {
		if(award["Funding Award"]["Start Date"])
			award["Funding Award"]["Start Date"] = Date.parse(award["Funding Award"]["Start Date"]);
		if(award["Funding Award"]["End Date"])
			award["Funding Award"]["End Date"] = Date.parse(award["Funding Award"]["End Date"]);
	}	

    module.exports.Harvester = Harvester;

}());
