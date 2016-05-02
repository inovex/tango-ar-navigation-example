# Project Tango AR Navigation Example

This is a small example implementation of an augmented reality path 
finding navigation using Project Tango. 

* walkable floor plan is tracked inside a [quadtree](https://de.wikipedia.org/wiki/Quadtree)
* navigation through the quadtree using [A*](https://de.wikipedia.org/wiki/A*-Algorithmus) with euclidean heuristic
* the floor plan is shown in a top view and can be rotated and scaled with multitouch gestures


![Screenshot](screenshot.png)

 
### Development

Missing assets can be installed by `./gradlew installAssets`
