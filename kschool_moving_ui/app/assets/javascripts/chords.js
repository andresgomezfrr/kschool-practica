var kschoolMoving = {}

kschoolMoving.getData = function(intervals){

  var intervals = intervals || "2016-01-28T12:05:00/2016-01-28T14:05:00";

  var query = {
    "queryType": "groupBy",
    "dataSource": "moving",
    "dimensions": ["new_building", "old_building"],
    "granularity": "all",
    "aggregations":[
                      {"type":"hyperUnique", "fieldName":"clients", "name":"clients"}
                   ],
    "intervals":[intervals]
  };

  $.ajax({
    url: "http://83.55.109.143:8082/druid/v2/?pretty=true",
    type: "POST",
    data: JSON.stringify(query),
    dataType: "json",
    contentType: "application/json; charset=utf-8"
  }).done(function(data){

  });
}

kschoolMoving.generateMatrix = function(data) {
  var n = Object.keys(data).length,
      result = new Array(n),
      i = 0,
      j = 0;

  for (var key in data) {
    n = Object.keys(data[key]).length;
    result[i] = new Array(n);
    j = 0;
    for (var subKey in data[key]) {
      result[i][j] = data[key][subKey];
      j++;
    }
    i++;
  }

  return result;
}

kschoolMoving.reDrawChords = function(opts) {
  $(opts.container)
    .find("svg")
    .remove();

  opts.matrix = this.generateMatrix(7);

  this.drawChords(opts);
}

kschoolMoving.drawChords = function(opts) {
  var matrix = opts.matrix,
      neighs = opts.neighs,
      container = opts.container;

  var palette = [
    "#638bdf", "#f26e57", "#89a54e", "#e89c4f", "#946acc",
    "#1758b5", "#c74531", "#3e812d", "#d87e34", "#734ca2",
    "#63a0e7", "#f4988a", "#a2bb77", "#edbc6c", "#be90fd",
    "#0f3742", "#783234", "#55633f", "#fdc52b", "#371c39",
    "#38606b", "#996062", "#6f795f", "#878787", "#8a3b8f",
    "#5d8d9a", "#ae9596", "#a8b298", "#bcbcbc", "#eb80f1"
  ];

  var w = 500,
      h = 500,
      r0 = Math.min(w, h) * .41,
      r1 = r0 * 1.1;

  var chord = d3.layout.chord()
    .padding(.05)
    .sortSubgroups(d3.descending)
    .matrix(matrix);

  var fill = d3.scale.category20c().range(palette),
      chord_svg = d3.svg.chord().radius(r0),
      arc_svg = d3.svg.arc().innerRadius(r0).outerRadius(r1);

  var svg = d3.select(container)
    .append("svg:svg")
      .attr("width", w)
      .attr("height", h)
    .append("svg:g")
      .attr("transform", "translate(" + w / 2 + "," + h / 2 + ")");

  svg.append("svg:g")
    .selectAll("path")
      .data(chord.groups)
    .enter().append("svg:path")
      .style("fill", function(d) { return fill(d.index); })
      .style("stroke", function(d) { return fill(d.index); })
      .attr("d", arc_svg)
      .on("mouseover", fade(.1))
      .on("mouseout", fade(.9));


  svg.append("g")
        .attr("class", "chord")
      .selectAll("path")
        .data(chord.chords)
      .enter().append("path")
        .style("fill", function(d) { return fill(d.source.index); })
        .attr("d", chord_svg)
        .style("fill-opacity", .6)
        .style("stroke", "#000")
        .style("stroke-width", ".15px")
        .append("title").text(function(d) {
          return neighs[d.source.index]
            + " → " + neighs[d.target.index]
            + ": " + Math.round(d.source.value)
            + "\n" + neighs[d.target.index]
            + " → " + neighs[d.source.index]
            + ": " + Math.round(d.target.value);
    });;

  drawTicks();

  function fade(opacity) {
    return function(g, i) {
      svg.selectAll("g.chord path")
          .filter(function(d) {
            return d.source.index != i && d.target.index != i;
          })
        .transition()
          .style("opacity", opacity);
    };
  }

  function groupTicks(d) {
    var k = (d.endAngle - d.startAngle) / d.value;
    return [{
      angle: d.value * k / 2 + d.startAngle,
      label: neighs[d.index]
    }];
  }

  function drawTicks() {
    var ticks = svg.append("g")
      .attr("class", "ticks")
      .attr("opacity", 0.1)
      .selectAll("g")
        .data(chord.groups)
      .enter().append("g")
      .selectAll("g")
        .data(groupTicks)
      .enter().append("g")
        .attr("transform", function(d) {
          return "rotate(" + (d.angle * 180 / Math.PI - 90) + ")"
              + "translate(" + r1 + ",0)";
        });
    ticks.append("line")
        .attr("x1", 1)
        .attr("y1", 0)
        .attr("x2", 5)
        .attr("y2", 0)
        .style("stroke", "#000");
    ticks.append("text")
        .attr("x", 8)
        .attr("dy", ".35em")
        .attr("text-anchor", function(d) {
          return d.angle > Math.PI ? "end" : null;
        })
        .attr("transform", function(d) {
          return d.angle > Math.PI ? "rotate(180)translate(-16)" : null;
        })
        .text(function(d) { return d.label; });

    svg.selectAll(".ticks").transition()
      .duration(340)
      .attr("opacity", 1);
  };
}

