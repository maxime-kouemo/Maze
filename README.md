# README #

### What is this repository for? ###


A simple implementation of a maze game. The user has to pan a path from one dot to the other one, in order to win. The maze is generated using the **disjoint set** algorithm. The disjoint set is a data structure that keeps track of a set of elements partitioned into a number of disjoint (non-overlapping) subsets.

There are three buttons:
* Edit size: allows you to choose a size for the square grid
* Generate: randomly generates a **perfect maze** grid whose number of rooms is equal to the square value of the size chosen above. There is no pre-saved set of maps/maze grids, by size. All the displayed grids, are randomly generated.
* Solution: draws the solution on the grid. The solution is the path between the two dots. The solution is calculated using the **depth first search** algorithm, upon the click on the "solution" button.

Currently the code is in french (variable names, comments and methods). 

The disjoint set use here was implemented by my former teacher (his name is in the file Disjointset).

 Example:

![Screenshot_2016-05-15-15-12-11.png](https://bitbucket.org/repo/MBxxor/images/1010793111-Screenshot_2016-05-15-15-12-11.png)

Edit: This version is an updated version, given Android's framework changes (build dependencies). The previous versions was from 2016 and can be found here(https://bitbucket.org/mamboa/maze) (I forgot the password of that account and can't edit it anymore.)