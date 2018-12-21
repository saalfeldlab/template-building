#@Dataset(label="Image A") im1
#@Dataset(label="Image B") im2

import net.imglib2.view.Views;

ssd = 0.0
c = Views.flatIterable( Views.interval( Views.pair( im1, im2 ), im2 )).cursor();
while( c.hasNext() )
{
	p = c.next();
	diff = p.getA().getRealDouble() - p.getB().getRealDouble();
	ssd += (diff * diff) ;
}

println( ssd )

def makeTable(listOfMaps) {
	table = new DefaultGenericTable()
	for (columnHeader in listOfMaps[0].keySet()) {
		column = new GenericColumn(columnHeader)
		for (row in listOfMaps) {
			column.add(row.get(columnHeader))
		}
		table.add(column)
	}
	return table	
}

table = makeTable( [['A':,'B',:,'SSD':ssd]])
tableDisplay = displayService.createDisplay(table)