import React from 'react';
import logo from './logo.svg';
import './App.css';

function App() {
    fetch('http://localhost:8080/greeting')
        .then(response => response.json())
        .then(data => {
            document.getElementById("anh").innerText = JSON.stringify(data)
            console.log('parsed json', data)
        }).catch(function (ex) {
        console.log('parsing failed', ex)
    })
    return (
        <div className="App">
            <h1 id="anh"></h1>
        </div>
    );
}

export default App;
