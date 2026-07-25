package com.demo;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/products")
public class ProductController {
  private final List<Product> products=List.of(new Product("P001","可乐","BRAND_A"),new Product("P002","矿泉水","BRAND_B"),new Product("P003","果汁","BRAND_A"));
  @GetMapping public List<Product> search(@RequestParam(required=false) String brandId){return brandId==null||brandId.isBlank()?products:products.stream().filter(p->p.brandId().equals(brandId)).toList();}
  public record Product(String id,String name,String brandId){}
}
