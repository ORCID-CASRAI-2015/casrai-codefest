
// Parse arguments
var stdio = require('stdio');

var options = stdio.getopt({
    'port': {description: 'The port that the web service listens on', args: 1},
    'db': {description: 'name of the MongoDB database this repeater uses for storing data (default: casrai-repeater)', args: 1},
    'col': {description: 'name of the MongoDB collection this repeater uses for storing data (default: data)', args: 1},
    'filter': {description: 'filters harvested data to include only awards with even or odd amounts (default: none) (options: even, odd)', args: 1}
});
if(!options.args || options.args.length == 0) {
	options.printHelp();
	return -1;
}


// Instanciate the storage module
var storageModule = require("./storageModule");
var storage = new storageModule.Storage(options.db || 'casrai-repeater', options.col || 'data');

// Instanciate the web service
var wsModule = require("./wsModule");
var ws = new wsModule.WS(storage, options.port || 8080, options.filter);

// Instanciate the harvester
var harvesterModule = require('./harvesterModule');
var harvester = new harvesterModule.Harvester(storage, options.args, options.filter)
