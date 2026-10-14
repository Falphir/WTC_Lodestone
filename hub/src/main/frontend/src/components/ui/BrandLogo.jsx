import logo from '../../assets/logo.png'

/**
 * The WTC Lodestone wordmark. It spells the name out, so its alt is the whole name of whatever
 * link it sits in -- don't put the name in text beside it. The width/height are the file's own
 * pixels, there only to hand the browser the aspect ratio before it decodes; CSS does the sizing,
 * so a caller that wants it smaller styles .brand-logo in its own scope rather than passing a size.
 */
export function BrandLogo() {
  return <img src={logo} alt="WTC Lodestone" className="brand-logo" width={200} height={112} />
}
