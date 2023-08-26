## Student Info

Victor Astinov
vastinov 20851407

## Technical Info
Tested with Android API level 30 on the Pixel C tablet with AVD. Primary testing was done on Samsung Galaxy S10, API level 31

## Application Info
The title appears at the top of the screen. Below is a toolbar with 4 buttons on the left and 3 on the right.

The 4 left buttons in order are, move to previous page, move to next page, undo action, redo action.

The 3 buttons on the right are toggle buttons, in order they are pen, hightlighter, eraser. On an orientation change or chaning to a new page the buttons are all toggled off, this is so that a user does not accidently draw/erase something while zooming in after changing orientation or when looking for new content. The pen color is in red as that provides a good amount of contrast, the highlighter is yellow. Erasing is done by swiping. Note that ONLY THE FIRST line that is swiped across is erased. THis is so that lines in close proximity are not deleted together. This posses a problem because when undoing a group delete of lines, they may not appear in the order they were swiped on. One at a time removal better suits the undo/redo features

Each page has its own undo/redo stack. Therefore undo/redo only apply to the actions visible to the user.

Zooming and Panning are kept when traversing pages. On device rotation the Zoom and pan are reset. This is so that the user can adapt to the new layout (as it would change shape and they might be looking somewhere else if it did not)

Centered at the bottom is the status bar with the form: current page/total pages


## Resources

Draw Icon:

https://www.flaticon.com/free-icon/edit_3388898

Eraser:

https://www.flaticon.com/free-icon/eraser_2891391

Highlight:

https://www.flaticon.com/free-icon/marker_3402274

Forward/Back:

https://cdn-icons-png.flaticon.com/512/3877/3877262.png

Undo:

https://cdn-icons-png.flaticon.com/512/60/60690.png

Redo:

https://static.thenounproject.com/png/1051633-200.png