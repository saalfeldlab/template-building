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
