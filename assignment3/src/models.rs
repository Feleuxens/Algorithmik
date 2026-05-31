type Length = u16;

pub struct BoolArray {
    data: Vec<Length>,
    counter: Length,
}

impl BoolArray {
    pub fn new(size: usize) -> Self {
        Self {
            data: vec![0; size],
            counter: 0,
        }
    }

    /// Sets the boolean at the given index to `true`.
    pub fn set(&mut self, index: usize) {
        self.data[index] = self.counter;
    }

    /// Returns the boolean at the given index.
    pub fn get(&self, index: usize) -> bool {
        self.data[index] == self.counter
    }

    /// Sets all booleans to `false`.
    pub fn clear(&mut self) {
        match self.counter {
            Length::MAX => {
                self.data.fill(0);
                self.counter = 1;
            }
            _ => self.counter += 1,
        }
    }
}


#[derive(Clone, Copy)]
pub struct Node {
    pub osm_id: u64,
    pub lat: f64,
    pub lon: f64,
    pub elevation: i32,
}

#[derive(Clone, Copy)]
pub struct Edge {
    pub target: u32,
    pub weight: u32,
}
