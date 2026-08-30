import '@testing-library/jest-dom';

// jsdom has no scroll implementation; components that call it (e.g. autoscroll
// on new messages) throw under RTL without this.
if (!Element.prototype.scrollTo) {
  Element.prototype.scrollTo = () => {};
}
