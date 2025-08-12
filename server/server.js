const express = require('express');
const app = express();
const PORT = 3000;

// Middleware to parse JSON
app.use(express.json());

// Simple logging middleware
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    next();
});

// Endpoint for SSID logs
app.post('/log', (req, res) => {
    console.log('📡 SSID Change Received:');
    console.log(JSON.stringify(req.body, null, 2));
    console.log('---');
    
    // Always respond with success
    res.json({ 
        status: 'success', 
        timestamp: new Date().toISOString() 
    });
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ status: 'healthy', uptime: process.uptime() });
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 SSID Logger Test Server running on http://0.0.0.0:${PORT}`);
    console.log(`📝 Logging endpoint: http://0.0.0.0:${PORT}/log`);
    console.log(`❤️  Health check: http://0.0.0.0:${PORT}/health`);
    console.log('\nWaiting for SSID logs...\n');
});