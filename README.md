	Data Flow System Requirements:

	1. Data Generator Service:
	   - Generates data containing:
	     * timestamp
	     * random integer (0-100)
	     * last 2 chars of MD5 hash of above values
	   - Produces 5 values per second
	   - Writes to a websocket

	2. Data Filter Service:
	   - Listens to websocket
	   - Filters data:
	     * If random value > 90: sends to message queue
	     * Otherwise: appends to file

	3. Message Queue Consumer Services:
	   a) Database Writer Service:
	      - Reads from queue
	      - Writes to database table
	      
	   b) MongoDB Writer Service:
	      - Reads from queue
	      - Writes to MongoDB collection
	      - Nests consecutive records in same document if hash > "99"
	      - Creates new document otherwise
